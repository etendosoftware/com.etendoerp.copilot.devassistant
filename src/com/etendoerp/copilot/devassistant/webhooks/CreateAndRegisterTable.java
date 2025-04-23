package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * The {@code CreateAndRegisterTable} class extends {@link BaseWebhookService} and provides functionality to
 * create a table in a PostgreSQL database and register it in Openbravo's AD_TABLE entity.
 * It combines the functionality of creating the table physically in the database and registering it in the system.
 */
public class CreateAndRegisterTable extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

  /**
   * Processes the incoming webhook request to create and register a table.
   * It creates the table in the database and then registers it in Openbravo's AD_TABLE entity.
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
    String tableName = StringUtils.isNotEmpty(parameter.get("DBTableName")) ? StringUtils.lowerCase(
        parameter.get("DBTableName")) : null;
    String dataAccessLevel = parameter.get("DataAccessLevel");
    String description = parameter.get("Description");
    String helpTable = parameter.get("Help");
    boolean isView = StringUtils.equalsIgnoreCase(parameter.get("IsView"), "true");

    try {
      Module module = Utils.getModuleByID(moduleID);
      List<ModuleDBPrefix> moduleDBPrefixList = module.getModuleDBPrefixList();
      if (moduleDBPrefixList.isEmpty()) {
        throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModuleNotFound"), moduleID));
      }
      String prefix = StringUtils.lowerCase(moduleDBPrefixList.get(0).getName());
      name = getDefaultName(name);
      tableName = determineTableName(name, prefix, tableName);
      javaClass = determineJavaClassName(name, javaClass);

      // Step 1: Create the table in the database
      createTableInDatabase(prefix, tableName, isView);

      // Step 2: Register the table in Etendo
      alreadyExistTable(tableName);
      DataPackage dataPackage = Utils.getDataPackage(module);
      Table adTable = createAdTable(dataPackage, javaClass, tableName, dataAccessLevel, description, helpTable, isView);

      // Step 3: Set response
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
   */
  private String determineTableName(String name, String prefix, String tableName) {
    if (StringUtils.isEmpty(tableName)) {
      tableName = StringUtils.startsWith(name, prefix) ? StringUtils.substring(StringUtils.removeStart(name, prefix),
          1) : name;
    }
    return StringUtils.startsWithIgnoreCase(tableName, prefix) ? tableName : prefix + "_" + tableName;
  }

  /**
   * Determines the Java class name based on the provided name and optional Java class name.
   *
   * @param name
   *     The base name of the table.
   * @param javaClass
   *     The optional Java class name provided in the parameters.
   * @return The final Java class name to use.
   */
  private String determineJavaClassName(String name, String javaClass) {
    if (StringUtils.isEmpty(javaClass) || StringUtils.equals(javaClass, "null")) {
      StringBuilder formattedName = new StringBuilder();
      String[] words = StringUtils.split(StringUtils.replaceChars(name, "_", " "), " ");
      for (String word : words) {
        if (StringUtils.isNotEmpty(word)) {
          formattedName.append(StringUtils.capitalize(word));
        }
      }
      return formattedName.toString();
    }
    return javaClass;
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

    // Usar StringBuilder para construir la consulta
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
   * Generates a constraint name based on the provided parameters. The generated name ensures that the length
   * does not exceed the maximum allowed length and makes adjustments to fit the constraint naming conventions.
   * <p>
   * If the name exceeds the maximum length, the name will be shortened by removing underscores or trimming
   * parts of the name until the length constraint is met.
   *
   * @param prefix
   *     the prefix to be used for the constraint name
   * @param name1
   *     the first part of the constraint name
   * @param name2
   *     the second part of the constraint name
   * @param suffix
   *     the suffix to be used for the constraint name
   * @return the generated constraint name
   * @throws SQLException
   *     if an error occurs during the SQL query execution
   * @throws JSONException
   *     if an error occurs while creating the JSON object
   */
  public static String getConstName(String prefix, String name1, String name2,
      String suffix) throws SQLException, JSONException {
    String fromName = StringUtils.substringAfter(name1, "_");
    String toName = StringUtils.substringAfter(name2, "_");
    String proposal = "";
    int offset = 0;
    while (StringUtils.isEmpty(proposal) || proposal.length() > MAX_LENGTH) {
      proposal = String.format("%s_%s_%s_%s", prefix, StringUtils.substring(fromName, 0, fromName.length() - offset),
          StringUtils.substring(toName, 0, toName.length() - offset), suffix);
      offset++;
    }

    String query = String.format(
        "SELECT count(1) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND constraint_name = '%s';",
        proposal);
    JSONObject response = Utils.executeQuery(query);
    int count = response.getJSONArray("result").getJSONObject(0).getInt("count");
    if (count > 0) {
      count++;
      proposal = String.format("%s_%s_%s%d_%s", prefix, StringUtils.substring(name1, 0, name1.length() - offset),
          StringUtils.substring(name2, 0, name2.length() - offset), count, suffix);
    }


    return proposal;
  }

  /**
   * Creates a new table in the system with the provided attributes.
   *
   * @param dataPackage
   *     The data package associated with the table.
   * @param javaClass
   *     The Java class name for the table.
   * @param tableName
   *     The name of the table.
   * @param dataAccessLevel
   *     The data access level for the table.
   * @param description
   *     The description of the table.
   * @param helpTable
   *     Help comment or description for the table.
   * @param isView
   *     Indicates if the table is a view.
   * @return The newly created table object.
   */
  private Table createAdTable(DataPackage dataPackage, String javaClass, String tableName, String dataAccessLevel,
      String description, String helpTable, boolean isView) {
    String name = tableName;
    Table adTable = OBProvider.getInstance().get(Table.class);
    adTable.setNewOBObject(true);
    Client client = OBDal.getInstance().get(Client.class, "0");
    adTable.setClient(client);
    adTable.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
    adTable.setActive(true);
    adTable.setCreationDate(new Date());
    adTable.setCreatedBy(OBContext.getOBContext().getUser());
    adTable.setUpdated(new Date());
    adTable.setUpdatedBy(OBContext.getOBContext().getUser());
    adTable.setDataAccessLevel(dataAccessLevel != null ? dataAccessLevel : "4"); // Default to "Client/Organization"
    adTable.setDataPackage(dataPackage);
    if (isView) {
      tableName = tableName + "_v";
      name = name + "V";
    }
    adTable.setName(name);
    adTable.setDBTableName(tableName);
    adTable.setJavaClassName(javaClass);
    adTable.setDescription(description);
    adTable.setHelpComment(helpTable);
    OBDal.getInstance().save(adTable);
    OBDal.getInstance().flush();

    return adTable;
  }

  /**
   * Checks if a table with the specified name already exists in the system.
   *
   * @param tableName
   *     The name of the table to check.
   * @return true if the table does not exist; false if the table exists.
   * @throws OBException
   *     if a table with the specified name already exists.
   */
  private boolean alreadyExistTable(String tableName) {
    OBCriteria<Table> tableNameCrit = OBDal.getInstance().createCriteria(Table.class);
    tableNameCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, tableName));
    tableNameCrit.setMaxResults(1);
    Table tableExist = (Table) tableNameCrit.uniqueResult();

    if (tableExist != null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_TableNameAlreadyUse")));
    }
    return true;
  }

}
