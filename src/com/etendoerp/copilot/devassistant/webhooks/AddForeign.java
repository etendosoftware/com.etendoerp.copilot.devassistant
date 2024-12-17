package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

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
   * @param parameter a Map containing the parameters for the webhook request.
   *                  Expected keys: "Prefix", "ParentTable", "ChildTable", "External".
   * @param responseVars a Map that will hold the response variables.
   *                     In case of success, a message will be added under the key "message".
   *                     In case of an error, an error message will be added under the key "error".
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");

    // Log the incoming parameters
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    // Extract parameters from the input map
    String prefix = parameter.get("Prefix");
    String parentTable = parameter.get("ParentTable");
    String childTable = parameter.get("ChildTable");
    String external = parameter.get("External");

    try {
      // Handle the prefix parameter
      if (prefix != null) {
        if (prefix.equalsIgnoreCase("None")) {
          prefix = childTable.split("_")[0];
        }
        prefix = prefix.toLowerCase();
      } else {
        prefix = "";
      }

      // Handle the parent table and child table parameters
      if (parentTable != null) {
        parentTable = parentTable.toLowerCase();
      } else {
        parentTable = "";
      }

      if (childTable != null) {
        childTable = childTable.toLowerCase();
      } else {
        childTable = "";
      }

      // Define column names and set the external boolean flag
      String parentTableId = parentTable + "_id";
      String parentColumn = parentTableId;
      boolean externalBool = StringUtils.equalsIgnoreCase(external, "true");

      // Adjust the child table name based on prefix
      if (childTable.startsWith(prefix + "_")) {
        childTable = childTable.substring(childTable.indexOf("_") + 1);
      }

      // Adjust the parent column based on external flag
      if (externalBool || !(parentTable.startsWith(prefix + "_"))) {
        parentColumn = "em_" + parentColumn;
      }

      // Add the new column to the child table
      AddColumn.addColumn(prefix, childTable, parentColumn, "ID", "", false);

      // Generate the foreign key constraint name
      String constraintFk = CreateTable.getConstName(prefix, childTable, parentTable, "fk");

      // Register the columns for the child table
      RegisterColumns.registerColumns(childTable);

      // Construct the SQL query to check if the foreign key exists and add it if not
      String query = "DO $$\n" +
          "DECLARE\n" +
          "    foreign_key_exists BOOLEAN;\n" +
          "BEGIN\n" +
          "    SELECT EXISTS (\n" +
          "        SELECT 1\n" +
          "        FROM pg_constraint c\n" +
          "        JOIN pg_class t ON c.conrelid = t.oid\n" +
          "        JOIN pg_namespace n ON t.relnamespace = n.oid\n" +
          "        JOIN pg_class r ON c.confrelid = r.oid\n" +
          "        WHERE c.contype = 'f'\n" +
          "        AND t.relname = '" +
          prefix +
          "_" +
          childTable +
          "'\n" +
          "        AND r.relname = '" +
          parentTable +
          "'\n" +
          "        AND c.conkey = ARRAY(SELECT attnum\n" +
          "                             FROM pg_attribute\n" +
          "                             WHERE attrelid = t.oid\n" +
          "                             AND attname = '" +
          parentColumn +
          "')\n" +
          "        AND c.confkey = ARRAY(SELECT attnum\n" +
          "                              FROM pg_attribute\n" +
          "                              WHERE attrelid = r.oid\n" +
          "                              AND attname = '" +
          parentTableId +
          "')\n" +
          "    ) INTO foreign_key_exists;\n" +
          "\n" +
          "    IF foreign_key_exists THEN\n" +
          "        UPDATE ad_column\n" +
          "        SET isparent = 'Y'\n" +
          "        WHERE name ILIKE '" +
          parentColumn +
          "'\n" +
          "        AND isupdateable = 'Y';\n" +
          "    ELSE\n" +
          "        UPDATE ad_column\n" +
          "        SET isparent = 'Y'\n" +
          "        WHERE name ILIKE '" +
          parentColumn +
          "'\n" +
          "        AND isupdateable = 'Y';\n" +
          "\n" +
          "        ALTER TABLE IF EXISTS public." +
          prefix +
          "_" +
          childTable +
          "\n" +
          "        ADD CONSTRAINT " +
          constraintFk +
          " FOREIGN KEY (" +
          parentColumn +
          ")\n" +
          "        REFERENCES public." +
          parentTable +
          " (" +
          parentTableId +
          ") MATCH SIMPLE\n" +
          "        ON UPDATE NO ACTION\n" +
          "        ON DELETE NO ACTION;\n" +
          "    END IF;\n" +
          "END $$;\n";

      Utils.executeQuery(query);

    } catch (Exception e) {
      // Handle errors and add error message to response
      responseVars.put("error", e.getMessage());
    }
  }
}
