package com.etendoerp.copilot.devassistant.webhooks;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.exception.NoConnectionAvailableException;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * This class handles adding columns to a database table. It is part of the webhook service and processes
 * requests to modify the schema of tables by adding new columns.
 */
public class CreateColumn extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

  private static final String TIMESTAMP_WITHOUT_TIMEZONE = "timestamp without time zone";
  private static final String VARCHAR32 = "character varying(32)";
  private static final String VARCHAR60 = "character varying(60)";
  private static final String CHAR1 = "character(1)";
  public static final String NUMERIC = "numeric";
  public static final String TABLEDIR_REFERENCE_ID = "17";

  /**
   * This method is invoked by the webhook to add a column to a table in the database.
   *
   * @param parameter
   *     A map containing parameters for the column addition, including the table ID, column name,
   *     column type, default value, and whether the column can be null.
   * @param responseVars
   *     A map that will hold the response values, including success or error messages.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }
    String tableId = parameter.get("tableID");
    String name = parameter.get("name");
    String moduleId = parameter.get("moduleID");
    String defaultParam = parameter.get("defaultValue");
    String referenceID = parameter.get("referenceID");
    String columnName = parameter.get("columnNameDB");
    String canBeNull = parameter.get("canBeNull");

    String targetTable = parameter.get("targetTable");

    Table table = OBDal.getInstance().get(Table.class, tableId);
    org.openbravo.model.ad.module.Module module = OBDal.getInstance().get(org.openbravo.model.ad.module.Module.class,
        moduleId);
    var reference = OBDal.getInstance().get(Reference.class, referenceID);
    var isExternal = module != table.getDataPackage().getModule();
    if (isTableDirRef(reference) && isExternal) {
      //the table dir cannot be used in when is an em_ column
      throw new OBException(OBMessageUtils.messageBD("COPDEV_ExternalTableDirRef"));
    }

    if (isTableDirRef(reference) && validateTableDir(table, columnName)) {
      //the table dir cannot be used in when is an em_ column
      throw new OBException(OBMessageUtils.messageBD("COPDEV_ExternalTableDirRef"));
    }

    if (Boolean.FALSE.equals(module.isInDevelopment())) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_ModuleNotInDevelopment"));
    }

    String dbTableName = table.getDBTableName();
    if (dbTableName == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_dbTableNameNotFound"));
    }

    String prefix = module.getModuleDBPrefixList().get(0).getName();
    if (isExternal) {

      columnName = "EM_" + prefix + "_" + columnName;
      name = "EM_" + prefix + "_ " + name;
    }


    try {
      JSONObject response = addColumn(prefix, dbTableName, columnName, reference, defaultParam,
          StringUtils.equalsIgnoreCase(canBeNull, "true"));

      Column newCol = OBProvider.getInstance().get(Column.class);
      newCol.setName(name);
      newCol.setDBColumnName(columnName);
      newCol.setTable(table);
      newCol.setModule(module);
      if (Boolean.TRUE.equals(reference.isBaseReference())) {
        newCol.setReference(reference);
      } else {
        newCol.setReferenceSearchKey(reference);
        newCol.setReference(reference.getParentReference());
      }
      newCol.setDefaultValue(defaultParam.replace("'", ""));
      OBDal.getInstance().save(newCol);
      OBDal.getInstance().flush();

      responseVars.put("response", response.toString());
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private boolean validateTableDir(Table table, String columnName) {
    //if is tableDir, must be end with _id
    if (StringUtils.endsWithIgnoreCase(columnName, "_id")) {
      throw new OBException("TableDir column name must follow the pattern <targetTable>_id");
    }
    //and the column name must match with a table
    String targetTable = columnName.substring(0, columnName.length() - 3);
    //check if the table exists
    var tableCrit = OBDal.getInstance().createCriteria(Table.class);
    tableCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, targetTable, MatchMode.EXACT));
    List<Table> tables = tableCrit.list();
    if (tables.isEmpty()) {
      throw new OBException(
          "Table " + targetTable + " does not exist. If the column is TableDir, the name must be the same as the target table name + _id");
    }
    //check if the column already exists
    var columnCrit = OBDal.getInstance().createCriteria(Column.class);
    columnCrit.add(Restrictions.ilike(Column.PROPERTY_DBCOLUMNNAME, columnName, MatchMode.EXACT));
    columnCrit.add(Restrictions.eq(Column.PROPERTY_TABLE, table));
    List<Column> columns = columnCrit.list();
    if (!columns.isEmpty()) {
      throw new OBException(
          "There is already a column with the same name in the table, please choose another name and use a Table type reference");
    }
    return true;
  }


  private boolean isTableDirRef(Reference reference) {
    return StringUtils.equals(reference.getId(), TABLEDIR_REFERENCE_ID);
  }

  /**
   * Validates if an external column is needed for the given table.
   * <p>
   * This method checks whether the column should be marked as external based on the table's module development status.
   * If the column is not external and the module is not in development, an exception is thrown.
   * </p>
   *
   * @param isExternal
   *     A boolean indicating whether the column is external.
   * @param table
   *     The table object to validate.
   * @throws OBException
   *     If the column is not external and the module is not in development.
   */
  public static void validateIfExternalNeeded(boolean isExternal, Table table) {
    if (!isExternal && !table.getDataPackage().getModule().isInDevelopment()) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_NeededExternalColumn"));
    }
  }

  /**
   * Adds a new column to the specified table in the database.
   *
   * @param prefix
   *     The prefix of the table.
   * @param tableName
   *     The name of the table to which the column will be added.
   * @param column
   *     The name of the column to be added.
   * @param
   * @param defaultValue
   *     The default value for the column.
   * @param canBeNull
   *     A boolean indicating whether the column can have null values.
   * @throws SQLException
   *     If there is an error during the database operation.
   */
  public static JSONObject addColumn(String prefix, String tableName, String column, Reference reference,
      String defaultValue, boolean canBeNull) throws SQLException, NoConnectionAvailableException {

    if (StringUtils.isBlank(column)) {
      column = String.format(OBMessageUtils.messageBD("COPDEV_DefaultColumnName"));
    } else {
      column = StringUtils.replace(StringUtils.trimToEmpty(column), " +", "_");
    }

    var dbType = getDbType(reference);

    String defaultState = StringUtils.isNotEmpty(defaultValue) ? " DEFAULT " + prepareDefaultValue(defaultValue,
        dbType) : "";
    String queryConstraint = " ";


    String queryNull = canBeNull ? " " : " NOT NULL";

    if (StringUtils.equals(dbType, CHAR1)) {
      queryConstraint = generateCheckConstraint(prefix, tableName, column);
    }

    String query = String.format("ALTER TABLE IF EXISTS public.%s ADD COLUMN IF NOT EXISTS %s %s %s %s %s;", tableName,
        column, dbType, queryNull, defaultState, queryConstraint);
    return Utils.executeQuery(query);
  }

  /**
   * Prepares the default value for a database column based on its type.
   * <p>
   * This method ensures that the default value is properly formatted according to the database type.
   * For character or varchar types, it adds single quotes around the value if not already present.
   * For timestamp types, it sets the default value to "now()" if the provided value is empty or null.
   * </p>
   *
   * @param defaultValue
   *     The default value to be prepared. Can be null or empty.
   * @param dbType
   *     The database type of the column (e.g., character, varchar, timestamp).
   * @return A string representing the prepared default value, formatted appropriately for the database type.
   */
  private static String prepareDefaultValue(String defaultValue, String dbType) {
    // If the column is a char / varchar type and the default value is not enclosed in single quotes, add them
    if (StringUtils.startsWithIgnoreCase(dbType, "character") && !StringUtils.startsWith(defaultValue, "'")) {
      return "'" + defaultValue + "'";
    }
    // If the column is a timestamp type and the default value is empty or null, set it to "now()"
    if (StringUtils.startsWithIgnoreCase(dbType, "timestamp") && (StringUtils.isEmpty(
        defaultValue) || StringUtils.equalsIgnoreCase(defaultValue, "null"))) {
      return " now() ";
    }
    // Return the default value as is if no special formatting is needed
    return defaultValue;
  }

  /**
   * Generates a SQL check constraint for a column in a database table.
   * <p>
   * This method creates a check constraint for a column, ensuring that its value
   * is restricted to 'Y' or 'N'. It also ensures that the constraint name does not
   * exceed the maximum allowed length by truncating and adjusting the name if necessary.
   * </p>
   *
   * @param prefix
   *     The prefix to be used in the constraint name.
   * @param tableName
   *     The name of the table to which the column belongs.
   * @param column
   *     The name of the column for which the constraint is being generated.
   * @return A SQL string representing the check constraint.
   */
  private static String generateCheckConstraint(String prefix, String tableName, String column) {
    String queryConstraint;
    String proposal = prefix + "_" + column + "_chk";
    String columnOff = column;
    var offset = 1;
    while ((proposal.length() > MAX_LENGTH) && (offset < 15)) {
      String nameOff = StringUtils.isNotEmpty(tableName) && tableName.length() > offset ? StringUtils.substring(
          tableName, offset) : tableName;
      columnOff = StringUtils.isNotEmpty(column) && column.length() > offset ? StringUtils.substring(column,
          offset) : column;
      proposal = prefix + "_" + nameOff + "_" + columnOff + "_chk";
      offset++;
    }
    queryConstraint = String.format(", ADD CONSTRAINT %s CHECK (%s = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))", proposal,
        columnOff);
    return queryConstraint;
  }

  /**
   * Retrieves the corresponding database type for the given column type.
   * <p>
   * This method maps a column type to its corresponding database type based on predefined mappings.
   * If the column type is not found in the mappings, it checks the parent reference type.
   * If neither is found, an exception is thrown.
   * </p>
   *
   * @param columnType
   *     The column type as a `Reference` object.
   * @return The corresponding database type as a string.
   * @throws OBException
   *     If the column type or its parent reference is not found in the mappings.
   */
  private static String getDbType(Reference columnType) {
    Map<String, String> mapping = new HashMap<>();
    mapping.put("Absolute DateTime", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("Absolute Time", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("Amount", NUMERIC);
    mapping.put("Assignment", VARCHAR32);
    mapping.put("Binary", "bytea");
    mapping.put("Button", CHAR1);
    mapping.put("Button List", VARCHAR60);
    mapping.put("Color", VARCHAR60);
    mapping.put("Date", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("DateTime", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("DateTime_From (Date)", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("DateTime_To (Date)", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("General Quantity", NUMERIC);
    mapping.put("ID", VARCHAR32);
    mapping.put("Image", VARCHAR60);
    mapping.put("Image BLOB", VARCHAR32);
    mapping.put("Integer", NUMERIC);
    mapping.put("Link", "character varying(200)");
    mapping.put("List", VARCHAR60);
    mapping.put("Masked String", VARCHAR60);
    mapping.put("Memo", "character varying(4000)");
    mapping.put("Non Transactional Sequence", VARCHAR60);
    mapping.put("Number", NUMERIC);
    mapping.put("OBKMO_Widget in Form Reference", VARCHAR32);
    mapping.put("OBUISEL_Multi Selector Reference", VARCHAR60);
    mapping.put("OBUISEL_SelectorAsLink Reference", NUMERIC);
    mapping.put("OBUISEL_Selector Reference", VARCHAR60);
    mapping.put("Password (decryptable)", "character varying(255)");
    mapping.put("Password (not decryptable)", "character varying(255)");
    mapping.put("PAttribute", VARCHAR32);
    mapping.put("Price", NUMERIC);
    mapping.put("Product Characteristics", "character varying(2000)");
    mapping.put("Quantity", NUMERIC);
    mapping.put("Rich Text Area", "text");
    mapping.put("RowID", VARCHAR60);
    mapping.put("Search", VARCHAR32);
    mapping.put("Search Vector", VARCHAR60);
    mapping.put("String", "character varying(200)");
    mapping.put("Table", VARCHAR32);
    mapping.put("TableDir", VARCHAR32);
    mapping.put("Text", "text");
    mapping.put("Time", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("Transactional Sequence", VARCHAR60);
    mapping.put("Tree Reference", VARCHAR32);
    mapping.put("Window Reference", VARCHAR60);
    mapping.put("YesNo", CHAR1);

    if (mapping.containsKey(columnType.getName())) {
      return mapping.get(columnType.getName());
    }
    String parentRefName = columnType.getParentReference().getName();
    if (!columnType.isBaseReference() && mapping.containsKey(parentRefName)) {
      return mapping.get(parentRefName);
    }
    throw new OBException(OBMessageUtils.messageBD("COPDEV_ColumnTypeNotFound") + columnType.getName());
  }
}
