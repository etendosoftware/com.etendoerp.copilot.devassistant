package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.ui.Tab;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to validate table and column constraints in Etendo.
 * Ensures compliance with best practices and system rules.
 */
public class CheckTablesColumnHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final int MAX_COLUMN_NAME_LENGTH = 30;
  private static final int MAX_TABLE_NAME_LENGTH = 30;

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    log.info("Starting column validation for a specific table...");

    JSONArray errors = new JSONArray();

    try {
      // Obtener el ID de la tabla a validar desde los parámetros
      String tableId = parameter.get("TableID");

      if (tableId == null || tableId.isEmpty()) {
        responseVars.put("error", "No table ID provided for validation.");
        return;
      }

      // Buscar la tabla en la base de datos usando el ID
      Table table = OBDal.getInstance().get(Table.class, tableId);
      if (table == null) {
        responseVars.put("error", "Table with ID " + tableId + " not found.");
        return;
      }

      List<Column> columns = table.getADColumnList();
      for (Column column : columns) {
        JSONObject error = validateColumn(table, column);
        if (error != null) {
          errors.put(error);
        }
      }

      responseVars.put("message", errors.toString());
    } catch (Exception e) {
      log.error("Error during table validation", e);
      responseVars.put("error", e.getMessage());
    }
  }

  /**
   * Validates a specific column within a table.
   *
   * @param table  The table that contains the column.
   * @param column The column to validate.
   * @return A JSON object containing the validation errors, or null if no errors are found.
   */
  private JSONObject validateColumn(Table table, Column column) {
    try {
      JSONObject error = new JSONObject();

      // Check for self-referencing foreign keys
      Table referencedTable = getReferencedTable(column);
      if (referencedTable != null && referencedTable.equals(table)) {
        error.put("error", "Column " + column.getDBColumnName() +
            " in table " + table.getDBTableName() +
            " is a self-referencing foreign key, which is not allowed.");
      }

      // Ensure foreign keys link to a valid primary key
      if (referencedTable != null) {
        boolean hasPrimaryKey = referencedTable.getADColumnList().stream()
            .anyMatch(Column::isKeyColumn);
        if (!hasPrimaryKey) {
          error.put("error", "Column " + column.getDBColumnName() +
              " in table " + table.getDBTableName() +
              " does not link to a primary key in the referenced table.");
        }
      }

      // Validate TableDir columns have a valid reference table
      if ("TableDir".equals(column.getReference().getName()) && referencedTable == null) {
        error.put("error", "Column " + column.getDBColumnName() +
            " in table " + table.getDBTableName() +
            " is of type TableDir but does not have a valid reference table.");
      }

      // Check for column name length violations
      if (column.getDBColumnName().length() > MAX_COLUMN_NAME_LENGTH) {
        error.put("error", "Column " + column.getDBColumnName() +
            " in table " + table.getDBTableName() +
            " name is too long. Maximum allowed length is " + MAX_COLUMN_NAME_LENGTH + " characters.");
      }

      // Validate reference tables are linked to a superior tab
      if ("Table".equals(column.getReference().getName()) || "TableDir".equals(column.getReference().getName())) {
        Tab tab = getTabForTable(table);
        Tab referencedTab = getTabForTable(referencedTable);

        if (tab != null && referencedTab != null && referencedTab.getSequenceNumber() <= tab.getSequenceNumber()) {
          error.put("error", "Column " + column.getDBColumnName() +
              " in table " + table.getDBTableName() +
              " references a table that is not a superior tab.");
        }

        if (!column.isLinkToParentColumn()) {
          error.put("suggestion", "Suggestion: Link column " + column.getDBColumnName() +
              " in table " + table.getDBTableName() + " to the parent column.");
        }
      }

      return error.length() > 0 ? error : null;
    } catch (Exception e) {
      log.error("Error validating column " + column.getDBColumnName(), e);
      return null;
    }
  }

  /**
   * Checks if a column is a foreign key.
   *
   * @param column The column to check.
   * @return true if the column is a foreign key, false otherwise.
   */
  private boolean isForeignKey(Column column) {
    return column.getReference() != null &&
        ("18".equals(column.getReference().getId()) || "19".equals(column.getReference().getId()));
  }

  /**
   * Retrieves the table referenced by a foreign key column.
   *
   * @param column The column to check.
   * @return The referenced table, or null if not applicable.
   */
  private Table getReferencedTable(Column column) {
    if (!isForeignKey(column)) {
      return null;
    }

    Reference reference = column.getReferenceSearchKey();
    if (reference != null && !reference.getADReferencedTableList().isEmpty()) {
      return reference.getADReferencedTableList().get(0).getTable();
    }
    return null;
  }

  /**
   * Retrieves the tab associated with a table.
   *
   * @param table The table for which to find the corresponding tab.
   * @return The associated tab, or null if no tab is found.
   */
  private Tab getTabForTable(Table table) {
    if (table == null) {
      return null;
    }

    OBCriteria<Tab> criteria = OBDal.getInstance().createCriteria(Tab.class);
    criteria.add(Restrictions.eq(Tab.PROPERTY_TABLE, table));
    List<Tab> tabs = criteria.list();

    return tabs.isEmpty() ? null : tabs.get(0);
  }
}
