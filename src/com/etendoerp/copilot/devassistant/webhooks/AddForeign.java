package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.webhooks.AddColumn.validateIfExternalNeeded;
import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * This class handles the process of adding a foreign key constraint to a specified child table
 * by checking if the foreign key already exists. If not, it creates the constraint and updates
 * the corresponding column in the ad_column table.
 */
public class AddForeign extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * This method handles the webhook request to add a foreign key constraint
   * to a child table, checking if the constraint already exists, and updating the column if necessary.
   *
   * @param parameter
   *     a Map containing the parameters for the webhook request.
   *     Expected keys: "Prefix", "ParentTable", "ChildTable", "External".
   * @param responseVars
   *     a Map that will hold the response variables.
   *     In case of success, a message will be added under the key "message".
   *     In case of an error, an error message will be added under the key "error".
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");

    // Log the incoming parameters
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    // Extract parameters from the input map
    String prefix = parameter.get("ExternalModulePrefix");
    String childTable = parameter.get("FromTable");
    String parentTable = parameter.get("ToTable");
    String externalStr = parameter.get("IsExternal");
    boolean externalBool = StringUtils.isNotEmpty(externalStr) && StringUtils.equalsIgnoreCase(externalStr, "true");
    String canBeNullStr = parameter.get("CanBeNull");
    boolean canBeNull = StringUtils.isNotEmpty(canBeNullStr) && StringUtils.equalsIgnoreCase(canBeNullStr, "true");
    String customName = parameter.get("CustomName");

    try {
      // Handle the prefix parameter
      if (StringUtils.isNotBlank(prefix) && StringUtils.equalsIgnoreCase(prefix, "None")) {
        prefix = childTable.split("_")[0];
      }
      if (StringUtils.isNotEmpty(prefix)) {
        prefix = StringUtils.lowerCase(prefix);
      }

      // Handle the parent table and child table parameters
      parentTable = StringUtils.defaultString(parentTable).toLowerCase();
      childTable = StringUtils.defaultString(childTable).toLowerCase();

      // Check if the parent table exists
      Table childTableObj = Utils.getTableByDBName(childTable);
      if (childTableObj == null) {
        throw new OBException("Table " + childTable + " does not exist. Check if the table name is correct.");
      }
      validateIfExternalNeeded(externalBool, childTableObj);
      if (externalBool && StringUtils.isEmpty(customName)) {
        throw new OBException(OBMessageUtils.messageBD(
            "COPDEV_customNameRequiredFroExt"));// The custom name is required for columns from external modules
      }

      Table parentTableObj = Utils.getTableByDBName(parentTable);
      if (parentTableObj == null) {
        throw new OBException("Table " + parentTable + " does not exist. Check if the table name is correct.");
      }
      String columnName;
      if (StringUtils.isNotEmpty(customName)) {
        columnName = customName;
      } else {
        columnName = parentTable + "_id";
        boolean alreadyExists = Utils.columnExists(childTableObj, columnName);
        if (alreadyExists) {
          throw new OBException(OBMessageUtils.messageBD("COPDEV_ColumnExists"));
        }
      }


      // Add the new column to the child table
      JSONObject resp = AddColumn.addColumn(prefix, childTable, columnName, "ID", "", canBeNull,
          externalBool);

      // Generate the foreign key constraint name
      String constraintFk = CreateAndRegisterTable.getConstName(prefix, childTable, parentTable, "fk");

      // Register the columns for the child table
      RegisterColumns.registerColumns(childTable);

    responseVars.put("response", resp.toString());
    } catch (Exception e) {
      // Handle errors and add error message to response
      responseVars.put("error", e.getMessage());
    }
  }
}
