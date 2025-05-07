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
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.exception.OBException;
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
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for the CreateAndRegisterTable and CreateView webhooks in the Copilot Toolpack.
 */
public class CreateAndRegisterTableTest extends WeldBaseTest {

  private static final Logger LOG = LogManager.getLogger();
  private static final String TEST_PREFIX = "COPDALT";
  private static final String MODULE_ID_KEY = "ModuleID";
  private static final String ERROR_KEY = "error";
  private static final String MESSAGE_KEY = "message";
  private static final String WEBHOOK = "Webhook";
  private static final String ERROR_FROM_WEBHOOK = "Error from " + WEBHOOK + ": ";
  private static final String WEBHOOK_FAILED_ERROR = WEBHOOK + " failed with error: ";
  private String testModuleId;
  private String testModulePrefixId;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    vars.setSessionValue("#User_Client", OBContext.getOBContext().getCurrentClient().getId());
    RequestContext.get().setVariableSecureApp(vars);
    OBPropertiesProvider.setInstance(new OBPropertiesProvider());

    Module mod = OBProvider.getInstance().get(Module.class);
    mod.setNewOBObject(true);
    mod.setInDevelopment(true);
    mod.setName("My Test Hook");
    mod.setJavaPackage("com.etendoerp.copilot.devassistant.testhook");
    mod.setVersion("1.0.0");
    mod.setDescription("Test module for CreateAndRegisterTable Webhook");
    mod.setType("M");
    OBDal.getInstance().save(mod);

    DataPackage dataPackage = OBProvider.getInstance().get(DataPackage.class);
    dataPackage.setNewOBObject(true);
    dataPackage.setModule(mod);
    dataPackage.setName(mod.getJavaPackage());
    dataPackage.setJavaPackage(mod.getJavaPackage() + ".datapackage");
    OBDal.getInstance().save(dataPackage);

    ModuleDBPrefix dbPrefix = OBProvider.getInstance().get(ModuleDBPrefix.class);
    dbPrefix.setModule(mod);
    dbPrefix.setName(TEST_PREFIX);
    dbPrefix.setNewOBObject(true);
    OBDal.getInstance().save(dbPrefix);

    OBDal.getInstance().flush();
    OBDal.getInstance().commitAndClose();

    Module refreshedMod = OBDal.getInstance().get(Module.class, mod.getId());
    List<DataPackage> dataPackages = refreshedMod.getDataPackageList();
    LOG.info("DataPackages associated with module '{}': {}", refreshedMod.getName(), dataPackages.size());
    for (DataPackage dp : dataPackages) {
      LOG.info(" - DataPackage: {}", dp.getName());
    }

    testModuleId = mod.getId();
    testModulePrefixId = dbPrefix.getId();
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
    assertTrue(message.contains("Table registered successfully with ID"));

    String tableName = TEST_PREFIX.toLowerCase() + "_my_table";
    assertTrue(tableExistsInDatabase(tableName));

    Table table = getTableByDBName(tableName);
    assertNotNull(table);
    assertEquals(tableName, table.getDBTableName().toLowerCase());
    assertEquals("4", table.getDataAccessLevel());

    dropTable(tableName);
  }

  @Test
  public void createAndRegisterViewTest() throws Exception {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    String tempTableName = TEST_PREFIX.toLowerCase() + "_temp_table";
    createTempTable(tempTableName);

    CreateView webhook = new CreateView();
    Map<String, String> parameter = new HashMap<>();
    parameter.put(MODULE_ID_KEY, testModuleId);
    parameter.put("Name", "my_view");
    String querySelect = String.format(
        "SELECT %s_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby " +
            "FROM %s",
        tempTableName, tempTableName
    );
    parameter.put("QuerySelect", querySelect);

    Map<String, String> responseVars = new HashMap<>();
    webhook.get(parameter, responseVars);

    if (responseVars.containsKey(ERROR_KEY)) {
      System.out.println(ERROR_FROM_WEBHOOK + responseVars.get(ERROR_KEY));
      fail(WEBHOOK_FAILED_ERROR + responseVars.get(ERROR_KEY));
    }
    assertTrue(responseVars.containsKey(MESSAGE_KEY));

    String viewName = TEST_PREFIX.toLowerCase() + "_my_view_v";
    assertTrue(tableExistsInDatabase(viewName));

    Table table = getTableByDBName(viewName);
    assertNotNull(table);
    assertEquals(viewName, table.getDBTableName().toLowerCase());
    assertEquals(TEST_PREFIX.toLowerCase() + "_my_viewV", table.getName());
    assertTrue(table.isView());

    dropTable(viewName);
    dropTable(tempTableName);
  }

  @Test
  public void tableAlreadyExistsTest() throws Exception {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    CreateAndRegisterTable webhook = new CreateAndRegisterTable();
    Map<String, String> parameter = new HashMap<>();
    parameter.put(MODULE_ID_KEY, testModuleId);
    parameter.put("Name", "duplicate_table");

    Map<String, String> responseVars = new HashMap<>();
    webhook.get(parameter, responseVars);

    if (responseVars.containsKey(ERROR_KEY)) {
      System.out.println(ERROR_FROM_WEBHOOK + responseVars.get(ERROR_KEY));
      fail(WEBHOOK_FAILED_ERROR + responseVars.get(ERROR_KEY));
    }
    String tableName = TEST_PREFIX.toLowerCase() + "_duplicate_table";
    assertTrue(tableExistsInDatabase(tableName));

    Table table = getTableByDBName(tableName);
    assertNotNull(table);

    responseVars.clear();
    webhook.get(parameter, responseVars);

    assertTrue(responseVars.containsKey(ERROR_KEY));
    String error = responseVars.get(ERROR_KEY);
    assertTrue(error.contains("COPDEV_TableNameAlreadyUse"));

    dropTable(tableName);
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
    assertTrue(error.contains("COPDEV_ModuleNotFound"));
  }

  private void createTempTable(String tableName) throws SQLException {
    String query = String.format(
        "CREATE TABLE IF NOT EXISTS %s ( " +
            "%s_id character varying(32) NOT NULL, " +
            "ad_client_id character varying(32) NOT NULL, " +
            "ad_org_id character varying(32) NOT NULL, " +
            "isactive character(1) NOT NULL DEFAULT 'Y', " +
            "created timestamp without time zone NOT NULL DEFAULT now(), " +
            "createdby character varying(32) NOT NULL, " +
            "updated timestamp without time zone NOT NULL DEFAULT now(), " +
            "updatedby character varying(32) NOT NULL, " +
            "CONSTRAINT %s PRIMARY KEY (%s_id)" +
            ")",
        tableName, tableName, tableName + "_pk", tableName
    );

    try {
      JSONObject response = Utils.executeQuery(query);
      LOG.info("Temporary table created: {}", response.toString());
    } catch (Exception e) {
      LOG.error("Failed to create temporary table {}: {}", tableName, e.getMessage(), e);
      throw new SQLException("Failed to create temporary table " + tableName, e);
    }
  }

  private boolean tableExistsInDatabase(String tableName) throws SQLException {
    try (Connection conn = OBDal.getInstance().getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = '" + tableName + "')")) {
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

  @After
  public void tearDown() throws Exception {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    try {
      Module mod = OBDal.getInstance().get(Module.class, testModuleId);
      if (mod != null) {
        List<DataPackage> dataPackages = mod.getDataPackageList();
        for (DataPackage dataPackage : dataPackages) {
          OBCriteria<Table> tableCrit = OBDal.getInstance().createCriteria(Table.class);
          tableCrit.add(Restrictions.eq(Table.PROPERTY_DATAPACKAGE, dataPackage));
          List<Table> tables = tableCrit.list();
          for (Table table : tables) {
            OBDal.getInstance().remove(table);
          }
        }
      }

      ModuleDBPrefix modPref = OBDal.getInstance().get(ModuleDBPrefix.class, testModulePrefixId);
      if (modPref != null) {
        OBDal.getInstance().remove(modPref);
      }
      if (mod != null) {
        OBDal.getInstance().remove(mod);
      }

      OBDal.getInstance().flush();
      OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      LOG.error("Error during tearDown: {}", e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      throw e;
    }
  }
}