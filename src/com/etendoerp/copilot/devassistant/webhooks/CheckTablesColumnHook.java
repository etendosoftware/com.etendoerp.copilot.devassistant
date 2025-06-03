package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.copilot.devassistant.TableRegistrationUtils;
import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to validate table and column constraints in Etendo.
 * Ensures compliance with best practices and system rules.
 */
public class CheckTablesColumnHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final int MAX_COLUMN_NAME_LENGTH = 30;
  private static final String TABLE_DIR_ID = "19";
  private static final String TABLE_ID = "18";
  public static final String ERROR = "error";

  @Override
  /**
   * Handles the GET request to validate columns of a specific table.
   * <p>
   * This method retrieves the table ID from the provided parameters, fetches the corresponding table
   * from the database, and validates its columns. Any validation errors are collected and returned
   * in the response variables.
   * </p>
   *
   * @param parameter
   *     A {@link Map} containing the request parameters, including the "TableID" of the table to validate.
   * @param responseVars
   *     A {@link Map} to store the response variables, including any validation errors or messages.
   */ public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    log.info("Starting column validation for a specific table...");

    JSONArray errors = new JSONArray();

    try {
      // Retrieve the table ID from the parameters
      String tableId = parameter.get("TableID");

      if (tableId == null || tableId.isEmpty()) {
        responseVars.put(ERROR, "No table ID provided for validation.");
        return;
      }

      // Fetch the table from the database using the ID
      Table table = OBDal.getInstance().get(Table.class, tableId);
      if (table == null) {
        responseVars.put(ERROR, "Table with ID " + tableId + " not found.");
        return;
      }

      // Register columns for the table
      TableRegistrationUtils.executeRegisterColumns(tableId);
      OBDal.getInstance().refresh(table);

      // Validate each column in the table
      List<Column> columns = table.getADColumnList();
      for (Column column : columns) {
        JSONObject error = validateColumn(table, column);
        if (error != null) {
          errors.put(error);
        }
      }

      // Add validation results to the response variables
      responseVars.put("message", errors.toString());
    } catch (Exception e) {
      log.error("Error during table validation", e);
      responseVars.put(ERROR, e.getMessage());
    }
  }

  /**
   * Validates a specific column within a table.
   *
   * @param table
   *     The table that contains the column.
   * @param column
   *     The column to validate.
   * @return A JSON object containing the validation errors, or null if no errors are found.
   */
  private JSONObject validateColumn(Table table, Column column) {
    try {
      JSONObject error = new JSONObject();


      // Validate TableDir points to a valid table
      validateTableDir(table, column, error);

      // Check for column name length violations
      if (column.getDBColumnName().length() > MAX_COLUMN_NAME_LENGTH) {
        error.put(ERROR,
            String.format("Column %s in table %s name is too long. Maximum allowed length is %d characters.",
                column.getDBColumnName(), table.getDBTableName(), MAX_COLUMN_NAME_LENGTH));
      }
      //check and modify table type
      var columnInAD = CreateColumn.getDbType(column.getReference());
      String typeInAD = columnInAD.getLeft();
      // check if the column in DB has the same type as in AD and the same length
      var query = String.format(
          "SELECT column_name, udt_name, character_maximum_length " + "FROM information_schema.columns " + "WHERE table_name ilike '%s' AND column_name ilike '%s'",
          column.getTable().getDBTableName(), column.getDBColumnName());
      JSONObject result = Utils.executeQuery(query);
      JSONArray resultArr = result.optJSONArray("result");
      if (resultArr == null || resultArr.length() <= 0) {
        return error.length() > 0 ? error : null;
      }
      JSONObject columnInfo = resultArr.getJSONObject(0);
      String typeInDB = columnInfo.optString("udt_name");
      if (StringUtils.equalsIgnoreCase(typeInDB, "bpchar")) {
        typeInDB = "character";
      }
      int lengthInDB = columnInfo.optInt("character_maximum_length", -1);

      if (needToApplyChangesInDB(column, typeInAD, typeInDB, lengthInDB)) {

        query = String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s", column.getTable().getDBTableName(),
            column.getDBColumnName(),
            typeInAD + ((column.getLength() != null) ? ("(" + column.getLength() + ")") : ""));
        execAndLog(query, error);
      }

      return error.length() > 0 ? error : null;
    } catch (Exception e) {
      log.error("Error validating column " + column.getDBColumnName(), e);
      return null;
    }
  }

  /**
   * Validates the TableDir reference of a column within a table.
   * <p>
   * This method checks if the column is a TableDir reference and validates its destination table.
   * It ensures that the destination table exists and is not self-referential. Any validation errors
   * are added to the provided JSON object.
   * </p>
   *
   * @param table
   *     The {@link Table} containing the column to validate.
   * @param column
   *     The {@link Column} being validated.
   * @param error
   *     A {@link JSONObject} to store validation errors, if any.
   * @throws JSONException
   *     If an error occurs while adding validation errors to the JSON object.
   */
  private static void validateTableDir(Table table, Column column, JSONObject error) throws JSONException {
    if (!isTableDirRef(column)) {
      return;
    }
    var destinationTable = column.getDBColumnName().toLowerCase();
    // Subtract "_id" from the column name
    if (destinationTable.endsWith("_id")) {
      destinationTable = destinationTable.substring(0, destinationTable.length() - 3);
    }
    OBCriteria<Table> tableCriteria = OBDal.getInstance().createCriteria(Table.class);
    tableCriteria.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, destinationTable));
    tableCriteria.setMaxResults(1);
    Table destinationTableObj = (Table) tableCriteria.uniqueResult();
    if (destinationTableObj == null) {
      error.put(ERROR,
          String.format(OBMessageUtils.messageBD("COPDEV_TableDirInvalidReference"), column.getDBColumnName(),
              table.getDBTableName()));
    }
    if (destinationTableObj != null && StringUtils.equalsIgnoreCase(column.getTable().getDBTableName(),
        destinationTableObj.getDBTableName())) {
      error.put(ERROR, String.format(OBMessageUtils.messageBD("COPDEV_SelfReferenceTableDir"), column.getDBColumnName(),
          table.getDBTableName()));
    }
  }

  /**
   * Executes a database query and logs any errors that occur.
   * <p>
   * This method attempts to execute the provided SQL query using the utility method.
   * If an exception is thrown during execution, it logs the error and adds a detailed
   * error message to the provided JSON object.
   * </p>
   *
   * @param query
   *     The SQL query to execute.
   * @param error
   *     A {@link JSONObject} to store error details if the query execution fails.
   * @throws JSONException
   *     If an error occurs while adding the error message to the JSON object.
   */
  private static void execAndLog(String query, JSONObject error) throws JSONException {
    try {
      Utils.executeQuery(query);
    } catch (Exception e) {
      String msg = "Error executing query: " + query + ". Error: " + e.getMessage() + ". Please try to fix the column type manually.";
      log.error(msg);
      error.put(ERROR, msg);
    }
  }

  /**
   * Determines whether changes need to be applied to a column in the database.
   * <p>
   * This method checks if the column's type or length in the database differs from the type or length
   * defined in the application dictionary (AD). It also ensures that timestamp types are not flagged
   * for changes if they match.
   * </p>
   *
   * @param column
   *     The {@link Column} object representing the column to check.
   * @param typeInAD
   *     The column type as defined in the application dictionary.
   * @param typeInDB
   *     The column type as defined in the database.
   * @param lengthInDB
   *     The length of the column as defined in the database.
   * @return {@code true} if changes need to be applied, {@code false} otherwise.
   */
  private static boolean needToApplyChangesInDB(Column column, String typeInAD, String typeInDB, int lengthInDB) {
    if (StringUtils.startsWithIgnoreCase(typeInAD, "timestamp") && StringUtils.startsWithIgnoreCase(typeInDB,
        "timestamp")) {
      return false;
    }

    return !StringUtils.equalsIgnoreCase(typeInAD, typeInDB) || column.getLength() != lengthInDB;
  }

  /**
   * Validates the relationship between a table and its referenced table.
   * <p>
   * This method checks if a column references a table that is not a superior tab. It also suggests
   * linking the column to the parent column if it is not already linked.
   * </p>
   *
   * @param table
   *     The {@link Table} containing the column to validate.
   * @param column
   *     The {@link Column} being validated.
   * @param referencedTable
   *     The {@link Table} referenced by the column.
   * @param error
   *     A {@link JSONObject} to store validation errors or suggestions.
   * @throws JSONException
   *     If an error occurs while adding data to the JSON object.
   */
  private void validateTabWithSuperior(Table table, Column column, Table referencedTable,
      JSONObject error) throws JSONException {
    if (isTableRef(column) || isTableDirRef(column)) {
      Tab tab = getTabForTable(table);
      Tab referencedTab = getTabForTable(referencedTable);

      if (tab != null && referencedTab != null && referencedTab.getSequenceNumber() <= tab.getSequenceNumber()) {
        error.put(ERROR,
            "Column " + column.getDBColumnName() + " in table " + table.getDBTableName() + " references a table that is not a superior tab.");
      }

      if (!column.isLinkToParentColumn()) {
        error.put("suggestion",
            "Suggestion: Link column " + column.getDBColumnName() + " in table " + table.getDBTableName() + " to the parent column.");
      }
    }
  }

  /**
   * Checks if the given column is a Table reference.
   * <p>
   * This method determines whether the column's reference ID matches the predefined Table ID.
   * </p>
   *
   * @param column
   *     The {@link Column} to check.
   * @return {@code true} if the column is a Table reference, {@code false} otherwise.
   */
  private static boolean isTableRef(Column column) {
    return StringUtils.equalsIgnoreCase(column.getReference().getId(), TABLE_ID);
  }

  /**
   * Checks if the given column is a TableDir reference.
   * <p>
   * This method determines whether the column's reference ID matches the predefined TableDir ID.
   * </p>
   *
   * @param column
   *     The {@link Column} to check.
   * @return {@code true} if the column is a TableDir reference, {@code false} otherwise.
   */
  private static boolean isTableDirRef(Column column) {
    return StringUtils.equalsIgnoreCase(column.getReference().getId(), TABLE_DIR_ID);
  }

  /**
   * Checks if a column is a foreign key.
   *
   * @param column
   *     The column to check.
   * @return true if the column is a foreign key, false otherwise.
   */
  private boolean isForeignKey(Column column) {
    return column.getReference() != null && ("18".equals(column.getReference().getId()) || "19".equals(
        column.getReference().getId()));
  }

  /**
   * Retrieves the table referenced by a foreign key column.
   *
   * @param column
   *     The column to check.
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
   * @param table
   *     The table for which to find the corresponding tab.
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
