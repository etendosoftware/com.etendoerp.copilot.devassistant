package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for the Webhooks in the Copilot Toolpack.
 */

public class DevAssistantWebhooksTests extends WeldBaseTest {
  public static final String DATA = "data";
  public static final String ID = "id";
  public static final String REFERENCE_ID_TEXT = "14";
  private static final String C_ORDER_TABLE_ID = "259";
  public static final String YESNO_REFERENCE_ID = "20";
  public static final String BP_TABLE_REF_ID = "138";
  public static final String PARAM_CAN_BE_NULL = "canBeNull";
  public static final String PARAM_COLUMN_NAME_DB = "columnNameDB";
  public static final String PARAM_DEFAULT_VALUE = "defaultValue";
  public static final String PARAM_MODULE_ID = "moduleID";
  public static final String PARAM_NAME = "name";
  public static final String PARAM_REFERENCE_ID = "referenceID";
  public static final String PARAM_TABLE_ID = "tableID";
  public static final String C_ORDER = "C_ORDER";
  public static final String RESPONSE = "response";
  public static final String MESSAGES = "messages";
  private String testModuleId;
  private String testModulePrefixId;
  private String testModuleDataPackageId;
  private static final Logger LOG = LogManager.getLogger();
  private static final String TEST_PREFIX = "COPDALT";
  private static final String MODULE_ID_KEY = "ModuleID";
  private static final String ERROR_KEY = "error";
  private static final String MESSAGE_KEY = "message";
  private static final String WEBHOOK = "Webhook";
  private static final String ERROR_FROM_WEBHOOK = "Error from " + WEBHOOK + ": ";
  private static final String WEBHOOK_FAILED_ERROR = WEBHOOK + " failed with error: ";

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception
   *     if an error occurs during setup
   */
  @Before
  public void setUp() throws Exception {
    super.setUp();

    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    OBDal.getInstance().flush();

    vars.setSessionValue("#User_Client", OBContext.getOBContext().getCurrentClient().getId());
    RequestContext.get().setVariableSecureApp(vars);
    OBPropertiesProvider.setInstance(new OBPropertiesProvider());

    Module mod = OBProvider.getInstance().get(Module.class);
    mod.setNewOBObject(true);
    mod.setInDevelopment(true);
    mod.setName("My Test Module");
    mod.setJavaPackage("com.etendoerp.copilot.devassistant.testmodule");
    mod.setVersion("1.0.0");
    mod.setDescription("Test module for Dev Assistant Webhooks");
    mod.setType("M");
    OBDal.getInstance().save(mod);
    OBDal.getInstance().flush();
    ModuleDBPrefix dbPrefix = OBProvider.getInstance().get(ModuleDBPrefix.class);
    dbPrefix.setModule(mod);
    dbPrefix.setName(TEST_PREFIX);
    dbPrefix.setNewOBObject(true);
    OBDal.getInstance().save(dbPrefix);
    OBDal.getInstance().flush();
    //data package
    var dataPackage = OBProvider.getInstance().get(DataPackage.class);
    dataPackage.setNewOBObject(true);
    dataPackage.setName(mod.getName() + " Data Package");
    dataPackage.setJavaPackage(mod.getJavaPackage() + ".data");
    dataPackage.setModule(mod);
    OBDal.getInstance().save(dataPackage);
    OBDal.getInstance().flush();
    testModuleId = mod.getId();
    testModulePrefixId = dbPrefix.getId();
    testModuleDataPackageId = dataPackage.getId();
  }

  /**
   * Test that creates a new String column in the C_ORDER table,
   * validates its creation, and then cleans up by removing the column.
   *
   * @throws Exception
   *     if an error occurs during column creation or removal
   */
  @Test
  public void createStringColumnTest() throws Exception {
    var ccw = new CreateColumn();
    Map<String, String> parameter = buildParameters("false", "mytext", "'hello'", testModuleId, "My Text Test",
        REFERENCE_ID_TEXT, C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_" + TEST_PREFIX + "_mytext", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn(C_ORDER, "em_" + TEST_PREFIX.toLowerCase() + "_mytext");

    validateResponse(respVars);
  }

  /**
   * Test that creates a new Yes/No (boolean) column in the C_ORDER table,
   * validates its creation, and then cleans up by removing the column.
   *
   * @throws Exception
   *     if an error occurs during column creation or removal
   */
  @Test
  public void createYesNoColumnTest() throws Exception {
    var ccw = new CreateColumn();
    Map<String, String> parameter = buildParameters("false", "myYesNo", "'N'", testModuleId, "My Boolean",
        YESNO_REFERENCE_ID, C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_" + TEST_PREFIX.toUpperCase() + "_myYesNo", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn(C_ORDER, "em_" + TEST_PREFIX.toLowerCase() + "_myYesNo");

    validateResponse(respVars);
  }

  /**
   * Validates the response map to ensure it contains valid data.
   * <p>
   * This method checks that the provided response map is not empty, retrieves the response string,
   * parses it into a JSON object, and verifies that the "messages" array within the JSON object
   * contains at least one message.
   * </p>
   *
   * @param respVars
   *     A {@link Map} containing the response variables to validate.
   * @throws JSONException
   *     If an error occurs while parsing the JSON response.
   */
  private static void validateResponse(Map<String, String> respVars) throws JSONException {
    assertFalse(respVars.keySet().isEmpty());
    String responseString = respVars.get(RESPONSE);
    JSONObject response = new JSONObject(responseString);
    JSONArray messages = response.getJSONArray(MESSAGES);
    assertTrue(messages.length() > 0);
  }

  /**
   * Builds a map of parameters required for column creation.
   * <p>
   * This method creates and populates a map with the necessary parameters for creating a column
   * in the database, including whether the column can be null, its database name, default value,
   * associated module ID, display name, reference ID, and table ID.
   * </p>
   *
   * @param canBeNull
   *     A {@link String} indicating whether the column can be null ("true" or "false").
   * @param dbColumnName
   *     The database name of the column.
   * @param defaultValue
   *     The default value for the column.
   * @param moduleId
   *     The ID of the module to which the column belongs.
   * @param name
   *     The display name of the column.
   * @param refererenceID
   *     The reference ID for the column.
   * @param tableId
   *     The ID of the table to which the column belongs.
   * @return A {@link Map} containing the parameters for column creation.
   */
  private Map<String, String> buildParameters(String canBeNull, String dbColumnName, String defaultValue,
      String moduleId, String name, String refererenceID, String tableId) {
    Map<String, String> parameter = new HashMap<>();
    parameter.put(PARAM_CAN_BE_NULL, canBeNull);
    parameter.put(PARAM_COLUMN_NAME_DB, dbColumnName);
    parameter.put(PARAM_DEFAULT_VALUE, defaultValue);
    parameter.put(PARAM_MODULE_ID, moduleId);
    parameter.put(PARAM_NAME, name);
    parameter.put(PARAM_REFERENCE_ID, refererenceID);
    parameter.put(PARAM_TABLE_ID, tableId);
    return parameter;
  }

  /**
   * Test that creates a new Table Reference column in the C_ORDER table,
   * validates its creation, and then cleans up by removing the column.
   *
   * @throws Exception
   *     if an error occurs during column creation or removal
   */
  @Test
  public void createTableReferenceColumnTest() throws Exception {
    var ccw = new CreateColumn();
    Map<String, String> parameter = new HashMap<>();
    parameter.put(PARAM_CAN_BE_NULL, "true");
    parameter.put(PARAM_COLUMN_NAME_DB, "otherbp");
    parameter.put(PARAM_MODULE_ID, testModuleId);
    parameter.put(PARAM_NAME, "My OtherBP");
    parameter.put(PARAM_REFERENCE_ID, BP_TABLE_REF_ID);
    parameter.put(PARAM_TABLE_ID, C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_" + TEST_PREFIX + "_otherbp", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn(C_ORDER, "em_" + TEST_PREFIX.toLowerCase() + "_otherbp");

    validateResponse(respVars);
  }

  /**
   * Retrieves a `Column` object based on its database column name and table ID.
   * <p>
   * This method creates a criteria query to search for a `Column` entity that matches
   * the specified database column name and table ID. If a match is found, it returns
   * the `Column` object; otherwise, it returns null.
   * </p>
   *
   * @param columnDBName
   *     The database column name to search for.
   * @param tableId
   *     The ID of the table to which the column belongs.
   * @return The `Column` object matching the specified criteria, or null if no match is found.
   */
  private static Column getColumn(String columnDBName, String tableId) {
    OBCriteria<Column> colCrit = OBDal.getInstance().createCriteria(Column.class);
    colCrit.add(Restrictions.eq(Column.PROPERTY_DBCOLUMNNAME, columnDBName));
    colCrit.add(Restrictions.eq(Column.PROPERTY_TABLE, OBDal.getInstance().get(Table.class, tableId)));
    colCrit.setMaxResults(1);
    return (Column) colCrit.uniqueResult();
  }

  /**
   * Drops a column from a database table.
   * <p>
   * This method constructs and executes an SQL query to remove a column from the specified table.
   * If an error occurs during the execution of the query, the exception is caught and its stack trace is printed.
   * </p>
   *
   * @param tablename
   *     The name of the table from which the column will be dropped.
   * @param columnname
   *     The name of the column to be dropped.
   */
  private static void dropColumn(String tablename, String columnname) throws SQLException {
    String query = "ALTER TABLE " + tablename + " DROP COLUMN " + columnname + ";";
    Utils.executeQuery(query);
  }

  @Test
  public void createAndRegisterBasicTableTest() throws Exception {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    CreateAndRegisterTable webhook = new CreateAndRegisterTable();
    Map<String, String> parameter = new HashMap<>();
    parameter.put(MODULE_ID_KEY, testModuleId);
    parameter.put("Name", "my_table");

    Map<String, String> responseVars = new HashMap<>();
    webhook.get(parameter, responseVars);

    if (responseVars.containsKey(ERROR_KEY)) {
      System.out.println(ERROR_FROM_WEBHOOK + responseVars.get(ERROR_KEY));
      fail(WEBHOOK_FAILED_ERROR + responseVars.get(ERROR_KEY));
    }
    assertTrue(responseVars.containsKey(MESSAGE_KEY));
    String message = responseVars.get(MESSAGE_KEY);
    assertTrue(message.contains("Table registered successfully"));

    String tableName = TEST_PREFIX.toLowerCase() + "_my_table";
    assertTrue(tableExistsInDatabase(tableName));

    Table table = getTableByDBName(tableName);
    assertNotNull(table);
    assertEquals(tableName, table.getDBTableName().toLowerCase());
    assertEquals("4", table.getDataAccessLevel());

    dropTable(tableName);
    OBDal.getInstance().remove(table);
    OBDal.getInstance().flush();
  }


  @Test
  public void invalidModuleIdTest() throws Exception {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    CreateAndRegisterTable webhook = new CreateAndRegisterTable();
    Map<String, String> parameter = new HashMap<>();
    parameter.put(MODULE_ID_KEY, "INVALID_MODULE_ID");
    parameter.put("Name", "my_table");

    Map<String, String> responseVars = new HashMap<>();
    webhook.get(parameter, responseVars);

    assertTrue(responseVars.containsKey(ERROR_KEY));
    String error = responseVars.get(ERROR_KEY);
    assertTrue(StringUtils.contains(error, "Module not found."));
  }


  private boolean tableExistsInDatabase(String tableName) throws SQLException {
    Connection conn = OBDal.getInstance().getConnection();
    try (
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = '" + tableName + "')")) {
      if (rs.next()) {
        return rs.getBoolean(1);
      }
      return false;
    }
  }

  private void dropTable(String tableName) throws SQLException {
    String query = "DROP TABLE IF EXISTS " + tableName + ";";
    try {
      JSONObject response = Utils.executeQuery(query);
      LOG.info("Table dropped: {}", response.toString());
    } catch (Exception e) {
      LOG.error("Failed to drop table {}: {}", tableName, e.getMessage(), e);
      throw new SQLException("Failed to drop table " + tableName, e);
    }
  }

  private Table getTableByDBName(String dbTableName) {
    OBCriteria<Table> tableCrit = OBDal.getInstance().createCriteria(Table.class);
    tableCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, dbTableName));
    tableCrit.setMaxResults(1);
    return (Table) tableCrit.uniqueResult();
  }


  /**
   * Cleans up the test environment after each test.
   */
  @After
  public void tearDown() {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);
    OBDal.getInstance().flush();

    ModuleDBPrefix modPrefix = OBDal.getInstance().get(ModuleDBPrefix.class, testModulePrefixId);
    if (modPrefix != null) {
      OBDal.getInstance().remove(modPrefix);
    }
    DataPackage dataPackage = OBDal.getInstance().get(DataPackage.class, testModuleDataPackageId);
    if (dataPackage != null) {
      OBDal.getInstance().remove(dataPackage);
    }
    Module mod = OBDal.getInstance().get(Module.class, testModuleId);
    OBDal.getInstance().remove(mod);
    OBDal.getInstance().flush();

  }
}
