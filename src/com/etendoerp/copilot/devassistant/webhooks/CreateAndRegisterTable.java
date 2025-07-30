package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import com.etendoerp.copilot.devassistant.TableRegistrationUtils;

/**
 * The {@code CreateAndRegisterTable} class extends {@link BaseWebhookService} and provides functionality to
 * create a table in a PostgreSQL database and register it in Etendo's AD_TABLE entity.
 */
public class CreateAndRegisterTable extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

  /**
   * Processes the incoming webhook request to create and register a table.
   * It creates the table in the database and then registers it in Etendo's AD_TABLE entity.
   *
   * @param parameter
   *     A map containing the parameters from the incoming request.
   * @param responseVars
   *     A map where the response message or error will be stored.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String name = StringUtils.lowerCase(parameter.get("Name"));
    String moduleID = parameter.get("ModuleID");
    String javaClass = parameter.get("JavaClass");
    String tableName = StringUtils.isNotEmpty(parameter.get("DBTableName")) ? StringUtils.lowerCase(parameter.get("DBTableName")) : null;
    String dataAccessLevel = parameter.get("DataAccessLevel");
    String description = parameter.get("Description");
    String helpTable = parameter.get("Help");
    boolean isView = StringUtils.equalsIgnoreCase(parameter.get("IsView"), "true");

    try {
      // Step 1: Get the module and prefix
      Object[] moduleAndPrefix = TableRegistrationUtils.getModuleAndPrefix(moduleID);
      Module module = (Module) moduleAndPrefix[0];
      String prefix = (String) moduleAndPrefix[1];

      name = getDefaultName(name);
      tableName = determineTableName(name, prefix, tableName);
      javaClass = TableRegistrationUtils.determineJavaClassName(name, javaClass);

      // Step 2: Create the table in the database
      createTableInDatabase(prefix, tableName, isView);

      // Step 3: Register the table in Etendo
      TableRegistrationUtils.alreadyExistTable(tableName);
      DataPackage dataPackage = TableRegistrationUtils.getDataPackage(module);
      Table adTable = TableRegistrationUtils.createAdTable(dataPackage, javaClass, tableName, dataAccessLevel, description, helpTable, isView);

      // Step 4: Set response
      responseVars.put("message", String.format(OBMessageUtils.messageBD("COPDEV_TableRegistSucc"), adTable.getId()));

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
      OBDal.getInstance().getSession().clear();
    }
  }

  /**
   * Determines the table name based on the provided name, prefix, and optional table name.
   *
   * @param name
   *     The base name of the table.
   * @param prefix
   *     The prefix for the table name.
   * @param tableName
   *     The optional table name provided in the parameters.
   * @return The final table name to use.
   * */
  private String determineTableName(String name, String prefix, String tableName) {
    if (StringUtils.isEmpty(tableName)) {
      tableName = StringUtils.startsWith(name, prefix) ? StringUtils.substring(StringUtils.removeStart(name, prefix), 1) : name;
    }
    return StringUtils.startsWithIgnoreCase(tableName, prefix) ? tableName : prefix + "_" + tableName;
  }

  /**
   * Creates the table physically in the PostgreSQL database.
   *
   * @param prefix
   *     The prefix for the table name.
   * @param tableName
   *     The base name of the table.
   * @param isView
   *     Indicates if the table is a view.
   * @throws Exception
   *     If an error occurs during table creation.
   */
  private void createTableInDatabase(String prefix, String tableName, boolean isView) throws Exception {
    String constraintIsactive = getConstName(prefix, tableName, "isactive", "chk");
    String constraintPk = getConstName(prefix, tableName, "", "pk");
    String constraintFkClient = getConstName(prefix, tableName, "ad_client", "fk");
    String constraintFkOrg = getConstName(prefix, tableName, "ad_org", "fk");

    String finalTableName = isView ? tableName + "_v" : tableName;

    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("CREATE TABLE IF NOT EXISTS public.%s ( ").append(
        "%s_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, ").append(
        "ad_client_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, ").append(
        "ad_org_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, ").append(
        "isactive character(1) COLLATE pg_catalog.\"default\" NOT NULL DEFAULT 'Y'::bpchar, ").append(
        "created timestamp without time zone NOT NULL DEFAULT now(), ").append(
        "createdby character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, ").append(
        "updated timestamp without time zone NOT NULL DEFAULT now(), ").append(
        "updatedby character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, ").append(
        "CONSTRAINT %s PRIMARY KEY (%s_id), ").append("CONSTRAINT %s FOREIGN KEY (ad_client_id) ").append(
        "REFERENCES public.ad_client (ad_client_id) MATCH SIMPLE ").append("ON UPDATE NO ACTION ").append(
        "ON DELETE NO ACTION, ").append("CONSTRAINT %s FOREIGN KEY (ad_org_id) ").append(
        "REFERENCES public.ad_org (ad_org_id) MATCH SIMPLE ").append("ON UPDATE NO ACTION ").append(
        "ON DELETE NO ACTION, ").append(
        "CONSTRAINT %s CHECK (isactive = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))").append(") TABLESPACE pg_default;");

    String query = String.format(queryBuilder.toString(), finalTableName, finalTableName, constraintPk, finalTableName,
        constraintFkClient, constraintFkOrg, constraintIsactive);

    JSONObject response = Utils.executeQuery(query);
    LOG.info("Table created in database: {}", response.toString());
  }

  /**
   * Returns the default table name if the provided name is blank.
   *
   * @param name
   *     The name of the table.
   * @return The provided table name or a default name if the provided name is blank.
   */
  private String getDefaultName(String name) {
    if (StringUtils.isBlank(name)) {
      return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
    }
    return name;
  }

  /**
   * Generates a constraint name based on the provided prefix, names, and suffix.
   * Ensures the name does not exceed the maximum length and does not conflict with existing constraints.
   *
   * @param prefix The prefix to use for the constraint name.
   * @param name1  The first name component.
   * @param name2  The second name component.
   * @param suffix The suffix to append to the constraint name.
   * @return The generated constraint name.
   */
  public static String getConstName(String prefix, String name1, String name2, String suffix) {
    String adjustedName1 = adjustNameWithPrefix(name1, prefix);
    String adjustedName2 = adjustNameWithPrefix(name2, prefix);
    String proposal = buildProposal(prefix, adjustedName1, adjustedName2, suffix);

    proposal = adjustProposalLength(proposal, adjustedName1, adjustedName2, prefix, suffix);

    int count = checkConstraintExists(proposal);
    if (count > 0) {
      count++;
      proposal = String.format("%s_%s_%s%d_%s", prefix,
          StringUtils.substring(adjustedName1, 0, adjustedName1.length() - 1),
          StringUtils.substring(adjustedName2, 0, adjustedName2.length() - 1), count, suffix);
    }

    return proposal;
  }

  /**
   * Adjusts a name by removing the prefix if it starts with it.
   *
   * @param name   The name to adjust.
   * @param prefix The prefix to check and remove.
   * @return The adjusted name.
   */
  private static String adjustNameWithPrefix(String name, String prefix) {
    String prefixWithUnderscore = prefix + "_";
    if (StringUtils.startsWith(name, prefixWithUnderscore) ||
        StringUtils.startsWithIgnoreCase(name, prefixWithUnderscore)) {
      return StringUtils.substring(name, prefix.length() + 1);
    }
    return name;
  }

  /**
   * Builds the initial proposal for the constraint name.
   *
   * @param prefix The prefix to use.
   * @param name1  The first name component.
   * @param name2  The second name component.
   * @param suffix The suffix to append.
   * @return The initial proposal.
   */
  private static String buildProposal(String prefix, String name1, String name2, String suffix) {
    return prefix + "_" + name1 + "_" + name2 + "_" + suffix;
  }

  /**
   * Adjusts the proposal if it exceeds the maximum length by removing underscores or trimming characters.
   *
   * @param proposal The initial proposal.
   * @param name1    The first name component.
   * @param name2    The second name component.
   * @param prefix   The prefix used.
   * @param suffix   The suffix used.
   * @return The adjusted proposal.
   */
  private static String adjustProposalLength(String proposal, String name1, String name2,
      String prefix, String suffix) {
    String adjustedName1 = name1;
    String adjustedName2 = name2;
    String result = proposal;

    if (result.length() > MAX_LENGTH &&
        (StringUtils.contains(adjustedName1, "_") || StringUtils.contains(adjustedName2, "_"))) {
      adjustedName1 = adjustedName1.replace("_", "");
      adjustedName2 = adjustedName2.replace("_", "");
      result = buildProposal(prefix, adjustedName1, adjustedName2, suffix);
    }

    int offset = 1;
    while (result.length() > MAX_LENGTH && offset < 15) {
      adjustedName1 = trimName(adjustedName1, offset);
      adjustedName2 = trimName(adjustedName2, offset);
      result = buildProposal(prefix, adjustedName1, adjustedName2, suffix);
      offset++;
    }

    return result;
  }

  /**
   * Trims a name by removing characters from the start based on the offset.
   *
   * @param name   The name to trim.
   * @param offset The number of characters to trim from the start.
   * @return The trimmed name.
   */
  private static String trimName(String name, int offset) {
    if (name.length() > offset) {
      return StringUtils.substring(name, offset);
    }
    return name;
  }

  /**
   * Checks if a constraint with the given name already exists in the database.
   *
   * @param constraintName The name of the constraint to check.
   * @return The count of existing constraints with the given name.
   */
  private static int checkConstraintExists(String constraintName) {
    String query = String.format(
        "SELECT count(1) FROM information_schema.table_constraints " +
            "WHERE constraint_type = 'FOREIGN KEY' AND constraint_name = '%s';",
        constraintName);

    int count = 0;
    try {
      JSONObject response = Utils.executeQuery(query);
      if (response == null) {
        return count;
      }

      JSONArray resultArray = response.getJSONArray("result");
      if (resultArray == null || resultArray.length() == 0) {
        return count;
      }

      JSONObject resultObject = resultArray.getJSONObject(0);
      count = resultObject.getInt("count");
    } catch (Exception e) {
      LOG.error("Error checking constraint name '{}': {}", constraintName, e.getMessage(), e);
    }
    return count;
  }
}
