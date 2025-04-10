package com.etendoerp.copilot.toolpack.webhook;

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
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.copilot.devassistant.webhooks.CreateColumn;

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
  private AutoCloseable mocks;

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception
   *     if an error occurs during setup
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
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
    parameter.put("canBeNull", "false");
    parameter.put("columnNameDB", "mytext");
    parameter.put("defaultValue", "'hello'");
    parameter.put("moduleID", "77E11BDECDEB44008DD2235D259A77D7");
    parameter.put("name", "My Text Test");
    parameter.put("referenceID", REFERENCE_ID_TEXT);
    parameter.put("tableID", C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_COPDEV_mytext", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn("C_ORDER", "em_copdev_mytext");

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
    parameter.put("canBeNull", "false");
    parameter.put("columnNameDB", "myYesNo");
    parameter.put("defaultValue", "'N'");
    parameter.put("moduleID", "77E11BDECDEB44008DD2235D259A77D7");
    parameter.put("name", "My Boolean");
    parameter.put("referenceID", YESNO_REFERENCE_ID);
    parameter.put("tableID", C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_COPDEV_myYesNo", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn("C_ORDER", "em_copdev_myYesNo");

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
    parameter.put("canBeNull", "true");
    parameter.put("columnNameDB", "otherbp");
    parameter.put("moduleID", "77E11BDECDEB44008DD2235D259A77D7");
    parameter.put("name", "My OtherBP");
    parameter.put("referenceID", BP_TABLE_REF_ID);
    parameter.put("tableID", C_ORDER_TABLE_ID);

    Map<String, String> respVars = new HashMap<>();
    ccw.get(parameter, respVars);

    Column col = getColumn("EM_COPDEV_otherbp", C_ORDER_TABLE_ID);
    assertNotNull(col);
    OBDal.getInstance().remove(col);
    OBDal.getInstance().flush();
    dropColumn("C_ORDER", "em_copdev_otherbp");

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
    OBCriteria<Column> col_crit = OBDal.getInstance().createCriteria(Column.class);
    col_crit.add(Restrictions.eq(Column.PROPERTY_DBCOLUMNNAME, columnDBName));
    col_crit.add(Restrictions.eq(Column.PROPERTY_TABLE, OBDal.getInstance().get(Table.class, tableId)));
    col_crit.setMaxResults(1);
    Column col = (Column) col_crit.uniqueResult();
    return col;
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
    OBDal.getInstance().commitAndClose();
  }
}
