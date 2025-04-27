package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
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
 * create a table in a PostgreSQL database and register it in Openbravo's AD_TABLE entity.
 */
public class CreateAndRegisterTable extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

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

      // Step 3: Register the table in Openbravo
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

  private String determineTableName(String name, String prefix, String tableName) {
    if (StringUtils.isEmpty(tableName)) {
      tableName = StringUtils.startsWith(name, prefix) ? StringUtils.substring(StringUtils.removeStart(name, prefix),
          1) : name;
    }
    return StringUtils.startsWithIgnoreCase(tableName, prefix) ? tableName : prefix + "_" + tableName;
  }

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

  private String getDefaultName(String name) {
    if (StringUtils.isBlank(name)) {
      return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
    }
    return name;
  }

  public static String getConstName(String prefix, String name1, String name2, String suffix) {
    if (StringUtils.startsWith(name1, prefix + "_") || StringUtils.startsWithIgnoreCase(name1, prefix + "_")) {
      name1 = StringUtils.substring(name1, prefix.length() + 1);
    }
    if (StringUtils.startsWith(name2, prefix + "_") || StringUtils.startsWithIgnoreCase(name2, prefix + "_")) {
      name2 = StringUtils.substring(name2, prefix.length() + 1);
    }

    String proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;

    if (proposal.length() > MAX_LENGTH && (StringUtils.contains(name1, "_") || StringUtils.contains(name2, "_"))) {
      name1 = name1.replace("_", "");
      name2 = name2.replace("_", "");
      proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;
    }

    int offset = 1;
    while (proposal.length() > MAX_LENGTH && offset < 15) {
      String name1Offsetted = name1.length() > offset ? StringUtils.substring(name1, offset) : name1;
      String name2Offsetted = name2.length() > offset ? StringUtils.substring(name2, offset) : name2;
      proposal = prefix + "_" + name1Offsetted + "_" + name2Offsetted + "_" + suffix;
      offset++;
    }

    String query = String.format(
        "SELECT count(1) FROM information_schema.table_constraints WHERE constraint_type = 'FOREIGN KEY' AND constraint_name = '%s';",
        proposal);

    int count = 0;
    try {
      JSONObject response = Utils.executeQuery(query);
      if (response != null) {
        JSONArray resultArray = response.getJSONArray("result");
        if (resultArray != null && resultArray.length() > 0) {
          JSONObject resultObject = resultArray.getJSONObject(0);
          count = resultObject.getInt("count");
        }
      }
    } catch (Exception e) {
      LOG.error("Error checking constraint name '{}': {}", proposal, e.getMessage(), e);
    }

    if (count > 0) {
      count++;
      proposal = String.format("%s_%s_%s%d_%s", prefix, StringUtils.substring(name1, 0, name1.length() - offset),
          StringUtils.substring(name2, 0, name2.length() - offset), count, suffix);
    }

    return proposal;
  }
}