package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for the CreateAndRegisterTable webhook in the Copilot Toolpack.
 */
public class CreateAndRegisterTableTest extends WeldBaseTest {

  private static final Logger LOG = LogManager.getLogger();
  private static final String TEST_PREFIX = "COPDEVT";
  private static final String MODULE_ID = "ModuleID";
  private static final String ERROR_KEY = "error";
  private static final String MESSAGE_KEY = "message";
  private static final String WEBHOOK = "Webhook";
  private static final String ERROR_FROM_WEBHOOK = "Error from " + WEBHOOK + ": ";
  private static final String WEBHOOK_FAILED_ERROR = WEBHOOK + " failed with error: ";
  private String testModuleId;
  private String testModulePrefixId;
  private String testDataPackageId;

  /**
   * Sets up the test environment before each test.
   * Creates a test module and a database prefix for use in the tests.
   */
  @Before
  public void setUp() throws Exception {
    super.setUp();

    // Set up the OBContext for the admin user
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    // Start a transaction
    OBDal.getInstance().getConnection().setAutoCommit(false);

    vars.setSessionValue("#User_Client", OBContext.getOBContext().getCurrentClient().getId());
    RequestContext.get().setVariableSecureApp(vars);
    OBPropertiesProvider.setInstance(new OBPropertiesProvider());

    try {
      // Create a test module
      Module mod = OBProvider.getInstance().get(Module.class);
      mod.setNewOBObject(true);
      mod.setInDevelopment(true);
      mod.setName("My Test Module");
      mod.setJavaPackage("com.etendoerp.copilot.devassistant.testmodule");
      mod.setVersion("1.0.0");
      mod.setDescription("Test module for CreateAndRegisterTable Webhook");
      mod.setType("M");
      OBDal.getInstance().save(mod);

      // Create a DataPackage for the module
      DataPackage dataPackage = OBProvider.getInstance().get(DataPackage.class);
      dataPackage.setNewOBObject(true);
      dataPackage.setModule(mod);
      dataPackage.setName("Test Data Package");
      dataPackage.setJavaPackage(mod.getJavaPackage() + ".datapackage");
      OBDal.getInstance().save(dataPackage);

      // Create a test database prefix
      ModuleDBPrefix dbPrefix = OBProvider.getInstance().get(ModuleDBPrefix.class);
      dbPrefix.setModule(mod);
      dbPrefix.setName(TEST_PREFIX);
      dbPrefix.setNewOBObject(true);
      OBDal.getInstance().save(dbPrefix);

      OBDal.getInstance().flush();

      testModuleId = mod.getId();
      testModulePrefixId = dbPrefix.getId();
      testDataPackageId = dataPackage.getId();

      // Commit the transaction
      OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      // Rollback the transaction on error
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }

  /**
   * Test that creates and registers a basic table using minimal parameters.
   * Verifies that the table is created in the database and registered in AD_TABLE.
   */
  @Test
  public void createAndRegisterBasicTableTest() throws Exception {
    // Start a transaction
    OBDal.getInstance().getConnection().setAutoCommit(false);

    try {
      CreateAndRegisterTable webhook = new CreateAndRegisterTable();
      Map<String, String> parameter = new HashMap<>();
      parameter.put(MODULE_ID, testModuleId);
      parameter.put("Name", "my_table");

      Map<String, String> responseVars = new HashMap<>();
      webhook.get(parameter, responseVars);

      // Verify the response
      if (responseVars.containsKey(ERROR_KEY)) {
        System.out.println(ERROR_FROM_WEBHOOK + responseVars.get(ERROR_KEY));
        fail(WEBHOOK_FAILED_ERROR + responseVars.get(ERROR_KEY));
      }
      assertTrue(responseVars.containsKey(MESSAGE_KEY));
      String message = responseVars.get(MESSAGE_KEY);
      assertTrue(message.contains("Table registered successfully with ID"));

      // Verify the table exists in the database
      String tableName = TEST_PREFIX.toLowerCase() + "_my_table";
      assertTrue(tableExistsInDatabase(tableName));

      // Verify the table is registered in AD_TABLE
      Table table = getTableByDBName(tableName);
      assertNotNull(table);
      assertEquals(tableName, table.getDBTableName().toLowerCase());
      assertEquals("4", table.getDataAccessLevel()); // Default value

      // Commit the transaction
      OBDal.getInstance().commitAndClose();

      // Clean up: Drop the table from the database
      dropTable(tableName);
    } catch (Exception e) {
      // Rollback the transaction on error
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }

  /**
   * Test that creates and registers a table as a view.
   * Verifies that the table is created with the "_v" suffix and registered correctly.
   */
  @Test
  public void createAndRegisterViewTableTest() throws Exception {
    // Start a transaction
    OBDal.getInstance().getConnection().setAutoCommit(false);

    try {
      CreateAndRegisterTable webhook = new CreateAndRegisterTable();
      Map<String, String> parameter = new HashMap<>();
      parameter.put(MODULE_ID, testModuleId);
      parameter.put("Name", "my_view");
      parameter.put("IsView", "true");

      Map<String, String> responseVars = new HashMap<>();
      webhook.get(parameter, responseVars);

      // Verify the response
      if (responseVars.containsKey(ERROR_KEY)) {
        System.out.println(ERROR_FROM_WEBHOOK + responseVars.get(ERROR_KEY));
        fail(WEBHOOK_FAILED_ERROR + responseVars.get(ERROR_KEY));
      }
      assertTrue(responseVars.containsKey(MESSAGE_KEY));

      // Verify the table exists in the database
      String tableName = TEST_PREFIX.toLowerCase() + "_my_view_v";
      assertTrue(tableExistsInDatabase(tableName));

      // Verify the table is registered in AD_TABLE
      Table table = getTableByDBName(tableName);
      assertNotNull(table);
      assertEquals(tableName, table.getDBTableName().toLowerCase());
      assertEquals(TEST_PREFIX.toLowerCase() + "_my_viewV", table.getName());

      // Commit the transaction
      OBDal.getInstance().commitAndClose();

      // Clean up: Drop the table from the database
      dropTable(tableName);
    } catch (Exception e) {
      // Rollback the transaction on error
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }

  /**
   * Test that attempts to create and register a table with a name that already exists.
   * Verifies that the appropriate error message is returned.
   */
  @Test
  public void tableAlreadyExistsTest() throws Exception {
    // Start a transaction
    OBDal.getInstance().getConnection().setAutoCommit(false);

    try {
      // First, create a table
      CreateAndRegisterTable webhook = new CreateAndRegisterTable();
      Map<String, String> parameter = new HashMap<>();
      parameter.put(MODULE_ID, testModuleId);
      parameter.put("Name", "duplicate_table");

      Map<String, String> responseVars = new HashMap<>();
      webhook.get(parameter, responseVars);

      // Verify the first creation was successful
      if (responseVars.containsKey(ERROR_KEY)) {
        System.out.println(ERROR_FROM_WEBHOOK + responseVars.get(ERROR_KEY));
        fail(WEBHOOK_FAILED_ERROR + responseVars.get(ERROR_KEY));
      }
      String tableName = TEST_PREFIX.toLowerCase() + "_duplicate_table";
      assertTrue(tableExistsInDatabase(tableName));

      // Attempt to create the same table again
      responseVars.clear();
      webhook.get(parameter, responseVars);

      // Verify the error
      assertTrue(responseVars.containsKey(ERROR_KEY));
      String error = responseVars.get(ERROR_KEY);
      assertTrue(error.contains("COPDEV_TableNameAlreadyUse"));

      // Commit the transaction
      OBDal.getInstance().commitAndClose();

      // Clean up: Drop the table from the database
      dropTable(tableName);
    } catch (Exception e) {
      // Rollback the transaction on error
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }

  /**
   * Test that attempts to create and register a table with an invalid prefix.
   * Verifies that the appropriate error message is returned.
   */
  @Test
  public void invalidPrefixTest() throws Exception {
    // Start a transaction
    OBDal.getInstance().getConnection().setAutoCommit(false);

    try {
      CreateAndRegisterTable webhook = new CreateAndRegisterTable();
      Map<String, String> parameter = new HashMap<>();
      parameter.put(MODULE_ID, "INVALIDID");
      parameter.put("Name", "my_table");

      Map<String, String> responseVars = new HashMap<>();
      webhook.get(parameter, responseVars);

      // Verify the error
      assertTrue(responseVars.containsKey(ERROR_KEY));
      String error = responseVars.get(ERROR_KEY);
      assertTrue(error.contains("COPDEV_PrefixNotFound"));

      // Commit the transaction
      OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      // Rollback the transaction on error
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }

  /**
   * Verifies if a table exists in the database.
   *
   * @param tableName
   *     The name of the table to check.
   * @return True if the table exists, false otherwise.
   */
  private boolean tableExistsInDatabase(String tableName) throws SQLException {
    try (Connection conn = OBDal.getInstance().getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(
             "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = '" + tableName + "')")) {
      if (rs.next()) {
        return rs.getBoolean(1);
      }
      return false;
    }
  }

  /**
   * Drops a table from the database.
   *
   * @param tableName
   *     The name of the table to drop.
   */
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

  /**
   * Retrieves a Table object by its database table name.
   *
   * @param dbTableName
   *     The database table name to search for.
   * @return The Table object, or null if not found.
   */
  private Table getTableByDBName(String dbTableName) {
    OBCriteria<Table> tableCrit = OBDal.getInstance().createCriteria(Table.class);
    tableCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, dbTableName));
    tableCrit.setMaxResults(1);
    return (Table) tableCrit.uniqueResult();
  }

  /**
   * Cleans up the test environment after each test.
   * Removes the test module and database prefix created during setup.
   */
  @After
  public void tearDown() throws Exception {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    // Start a transaction
    OBDal.getInstance().getConnection().setAutoCommit(false);

    try {
      // Remove the test module and prefix
      Module mod = OBDal.getInstance().get(Module.class, testModuleId);
      ModuleDBPrefix modPref = OBDal.getInstance().get(ModuleDBPrefix.class, testModulePrefixId);
      DataPackage dataPackage = OBDal.getInstance().get(DataPackage.class, testDataPackageId);
      if (modPref != null) {
        OBDal.getInstance().remove(modPref);
      }
      if (mod != null) {
        OBDal.getInstance().remove(mod);
      }
      if (dataPackage != null) {
        OBDal.getInstance().remove(dataPackage);
      }

      OBDal.getInstance().flush();
      OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }
}
