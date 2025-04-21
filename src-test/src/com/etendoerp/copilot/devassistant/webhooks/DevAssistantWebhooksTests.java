package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Test;
import org.junit.jupiter.api.BeforeAll;
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
  private String testModuleId;
  private String testModulePrefixId;

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception
   *     if an error occurs during setup
   */
  @BeforeAll
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
    dbPrefix.setName("COPDEVT");
    dbPrefix.setNewOBObject(true);
    OBDal.getInstance().save(dbPrefix);
    OBDal.getInstance().flush();
    testModuleId = mod.getId();
    testModulePrefixId = dbPrefix.getId();
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
    Map<String, String> parameter = new HashMap<>();
    parameter.put(PARAM_CAN_BE_NULL, "false");
    parameter.put(PARAM_COLUMN_NAME_DB, "mytext");
    parameter.put(PARAM_DEFAULT_VALUE, "'hello'");
    parameter.put(PARAM_MODULE_ID, testModuleId);
    parameter.put(PARAM_NAME, "My Text Test");
    parameter.put(PARAM_REFERENCE_ID, REFERENCE_ID_TEXT);
    parameter.put(PARAM_TABLE_ID, C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_COPDEVT_mytext", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn("C_ORDER", "em_copdevt_mytext");

    assertFalse(respVars.keySet().isEmpty());
    String responseString = respVars.get("response");
    JSONObject response = new JSONObject(responseString);
    JSONArray messages = response.getJSONArray("messages");
    assertTrue(messages.length() > 0);
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
    Map<String, String> parameter = new HashMap<>();
    parameter.put(PARAM_CAN_BE_NULL, "false");
    parameter.put(PARAM_COLUMN_NAME_DB, "myYesNo");
    parameter.put(PARAM_DEFAULT_VALUE, "'N'");
    parameter.put(PARAM_MODULE_ID, testModuleId);
    parameter.put(PARAM_NAME, "My Boolean");
    parameter.put(PARAM_REFERENCE_ID, YESNO_REFERENCE_ID);
    parameter.put(PARAM_TABLE_ID, C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_COPDEVT_myYesNo", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn("C_ORDER", "em_copdevt_myYesNo");

    assertFalse(respVars.keySet().isEmpty());
    String responseString = respVars.get("response");
    JSONObject response = new JSONObject(responseString);
    JSONArray messages = response.getJSONArray("messages");
    assertTrue(messages.length() > 0);
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

    Column col = getColumn("EM_COPDEVT_otherbp", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn("C_ORDER", "em_copdevt_otherbp");

    assertFalse(respVars.keySet().isEmpty());
    String responseString = respVars.get("response");
    JSONObject response = new JSONObject(responseString);
    JSONArray messages = response.getJSONArray("messages");
    assertTrue(messages.length() > 0);
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

    Module mod = OBDal.getInstance().get(Module.class, testModuleId);
    ModuleDBPrefix modPrefix = OBDal.getInstance().get(ModuleDBPrefix.class, testModulePrefixId);
    if (modPrefix != null) {
      OBDal.getInstance().remove(modPrefix);
    }
    OBDal.getInstance().remove(mod);
    OBDal.getInstance().flush();

    OBDal.getInstance().commitAndClose();
  }
}
