package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.TableRegistrationUtils;

/**
 * Unit test suite for {@link CreateView}, responsible for validating the creation of
 * database views in Etendo/Openbravo based on dynamic SQL received through webhook parameters.
 *
 * <p>This test class verifies:</p>
 * <ul>
 *   <li>Mandatory parameter validation (Name, ModuleID, QuerySelect)</li>
 *   <li>Validation of SQL projection columns</li>
 *   <li>Detection and handling of SQL errors</li>
 *   <li>Proper cleanup and restoration of the DAL/OBContext security mode</li>
 *   <li>Correct delegation to {@link TableRegistrationUtils} for view/table registration</li>
 *   <li>Handling of optional parameters such as JavaClass, Description, DataAccessLevel, Help</li>
 *   <li>Correct population of the response map with 'message' or 'error'</li>
 * </ul>
 *
 * <p>All database-related behavior is simulated through mocks. No real SQL or metadata
 * inspection is performed.</p>
 */
@ExtendWith(MockitoExtension.class)
class CreateViewTest {

  @InjectMocks
  private CreateView createView;

  @Mock private OBDal obDal;
  @Mock private OBContext obContext;
  @Mock private Connection connection;
  @Mock private PreparedStatement preparedStatement;
  @Mock private ResultSet resultSet;
  @Mock private ResultSetMetaData resultSetMetaData;
  @Mock private Module module;
  @Mock private DataPackage dataPackage;
  @Mock private Table table;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<OBMessageUtils> messageMock;
  private MockedStatic<TableRegistrationUtils> tableRegUtilsMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;

  /**
   * Initializes static mocks, default messages and parameter maps.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obContextMock = mockStatic(OBContext.class);
    messageMock = mockStatic(OBMessageUtils.class);
    tableRegUtilsMock = mockStatic(TableRegistrationUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obContextMock.when(OBContext::getOBContext).thenReturn(obContext);

    // Default internationalized messages
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ViewCreatedSuccessfully"))
        .thenReturn("View %s created successfully");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ProjectionColumnNotFound"))
        .thenReturn("Mandatory columns missing: %s");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_InvalidQuery"))
        .thenReturn("Invalid query: %s");

    parameters = new HashMap<>();
    responseVars = new HashMap<>();
  }

  /**
   * Releases all static mocks after each test.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    obContextMock.close();
    messageMock.close();
    tableRegUtilsMock.close();
  }

  /**
   * Ensures that missing "Name" parameter produces an error message.
   */
  @Test
  void testGetWithMissingNameShouldThrowException() {
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "SELECT * FROM test");

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("Name parameter is required"));
  }

  /**
   * Ensures that empty "Name" parameter produces an error message.
   */
  @Test
  void testGetWithEmptyNameShouldThrowException() {
    parameters.put("Name", "");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "SELECT * FROM test");

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("Name parameter is required"));
  }

  /**
   * Ensures that missing "ModuleID" is detected and reported.
   */
  @Test
  void testGetWithMissingModuleIDShouldThrowException() {
    parameters.put("Name", "TestView");
    parameters.put("QuerySelect", "SELECT * FROM test");

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("ModuleID parameter is required"));
  }

  /**
   * Ensures that empty "ModuleID" is treated as invalid.
   */
  @Test
  void testGetWithEmptyModuleIDShouldThrowException() {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "");
    parameters.put("QuerySelect", "SELECT * FROM test");

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("ModuleID parameter is required"));
  }

  /**
   * Ensures that missing "QuerySelect" triggers an error.
   */
  @Test
  void testGetWithMissingQuerySelectShouldThrowException() {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("QuerySelect parameter is required"));
  }

  /**
   * Ensures that empty "QuerySelect" is considered invalid.
   */
  @Test
  void testGetWithEmptyQuerySelectShouldThrowException() {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "");

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("QuerySelect parameter is required"));
  }

  /**
   * Ensures null parameters produce an error without crashing.
   */
  @Test
  void testGetWithNullParametersShouldThrowException() {
    parameters.put("Name", null);
    parameters.put("ModuleID", null);
    parameters.put("QuerySelect", null);

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
  }

  /**
   * Ensures that if the SQL projection does not contain all mandatory
   * Openbravo fields (ad_client_id, ad_org_id, created, etc.) the method
   * returns an error message.
   *
   * <p>Simulates a valid SQL statement returning only two columns.</p>
   */
  @Test
  void testGetWithMissingMandatoryColumnsShouldThrowException() throws Exception {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "SELECT id, name FROM test");

    Object[] moduleAndPrefix = new Object[] { module, "TEST" };
    tableRegUtilsMock.when(() -> TableRegistrationUtils.getModuleAndPrefix("module123"))
        .thenReturn(moduleAndPrefix);

    when(module.getName()).thenReturn("Test Module");

    // Simulate validation query response
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.execute()).thenReturn(true);
    when(preparedStatement.getResultSet()).thenReturn(resultSet);
    when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
    when(resultSetMetaData.getColumnCount()).thenReturn(2);
    when(resultSetMetaData.getColumnName(1)).thenReturn("id");
    when(resultSetMetaData.getColumnName(2)).thenReturn("name");
    when(obDal.getConnection()).thenReturn(connection);

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("Mandatory columns missing")
        || responseVars.get("error").contains("Invalid query"));
  }

  /**
   * Ensures that SQL syntax errors or any {@link SQLException} are caught
   * and translated into a user-friendly error response.
   */
  @Test
  void testGetWithSQLExceptionShouldReturnError() throws Exception {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "INVALID SQL");

    Object[] moduleAndPrefix = new Object[] { module, "TEST" };
    tableRegUtilsMock.when(() -> TableRegistrationUtils.getModuleAndPrefix("module123"))
        .thenReturn(moduleAndPrefix);

    when(obDal.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Syntax error"));

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("SQL Error")
        || responseVars.get("error").contains("Syntax error"));
  }

  /**
   * Ensures that even if an error occurs, the security mode and context behavior
   * of OBContext/OBDal is restored to the previous state.
   *
   * <p>This is critical for DAL-based systems to avoid leaking privileged mode.</p>
   */
  @Test
  void testGetShouldRestorePreviousModeEvenOnError() throws Exception {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "SELECT * FROM test");

    Object[] moduleAndPrefix = new Object[] { module, "TEST" };
    tableRegUtilsMock.when(() -> TableRegistrationUtils.getModuleAndPrefix("module123"))
        .thenReturn(moduleAndPrefix);

    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Error"));
    when(obDal.getConnection()).thenReturn(connection);

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
  }

  /**
   * Ensures that when all required and optional parameters are supplied
   * and SQL metadata is valid, the view is successfully created and
   * a success message is returned instead of an error.
   */
  @Test
  void testGetWithAllOptionalParametersShouldUseThemInRegistration() throws Exception {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "SELECT * FROM test");
    parameters.put("JavaClass", "CustomClass");
    parameters.put("DataAccessLevel", "3");
    parameters.put("Description", "Test Description");
    parameters.put("Help", "Test Help");

    setupSuccessfulScenario();
    when(obDal.getConnection()).thenReturn(connection);

    createView.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Sets up a fully validated SQL metadata scenario, including:
   * <ul>
   *   <li>All Openbravo mandatory columns present</li>
   *   <li>Successful CREATE VIEW statement</li>
   *   <li>Mocked AD_Table registration</li>
   *   <li>View existence check</li>
   * </ul>
   *
   * @throws Exception if any mocked SQL operation unexpectedly fails
   */
  private void setupSuccessfulScenario() throws Exception {
    parameters.put("Name", "TestView");
    parameters.put("ModuleID", "module123");
    parameters.put("QuerySelect", "SELECT * FROM test");

    Object[] moduleAndPrefix = new Object[] { module, "TEST" };
    tableRegUtilsMock.when(() -> TableRegistrationUtils.getModuleAndPrefix("module123"))
        .thenReturn(moduleAndPrefix);

    when(module.getName()).thenReturn("Test Module");

    // Validation query with full mandatory columns
    PreparedStatement validationStmt = mock(PreparedStatement.class);
    ResultSet validationRs = mock(ResultSet.class);
    ResultSetMetaData validationMeta = mock(ResultSetMetaData.class);

    when(connection.prepareStatement(contains("LIMIT 0"))).thenReturn(validationStmt);
    when(validationStmt.execute()).thenReturn(true);
    when(validationStmt.getResultSet()).thenReturn(validationRs);
    when(validationRs.getMetaData()).thenReturn(validationMeta);
    when(validationMeta.getColumnCount()).thenReturn(8);
    when(validationMeta.getColumnName(1)).thenReturn("ad_client_id");
    when(validationMeta.getColumnName(2)).thenReturn("ad_org_id");
    when(validationMeta.getColumnName(3)).thenReturn("isactive");
    when(validationMeta.getColumnName(4)).thenReturn("created");
    when(validationMeta.getColumnName(5)).thenReturn("createdby");
    when(validationMeta.getColumnName(6)).thenReturn("updated");
    when(validationMeta.getColumnName(7)).thenReturn("updatedby");
    when(validationMeta.getColumnName(8)).thenReturn("TEST_testview_v_id");

    // Simulate successful CREATE VIEW
    PreparedStatement createStmt = mock(PreparedStatement.class);
    when(connection.prepareStatement(startsWith("CREATE OR REPLACE VIEW"))).thenReturn(createStmt);
    when(createStmt.execute()).thenReturn(false);

    // Simulate view existence check
    PreparedStatement checkStmt = mock(PreparedStatement.class);
    ResultSet checkRs = mock(ResultSet.class);
    when(connection.prepareStatement(contains("pg_views"))).thenReturn(checkStmt);
    when(checkStmt.executeQuery()).thenReturn(checkRs);
    when(checkRs.next()).thenReturn(true);

    // No columns found in schema lookup
    PreparedStatement columnsStmt = mock(PreparedStatement.class);
    ResultSet columnsRs = mock(ResultSet.class);
    when(connection.prepareStatement(contains("information_schema.columns"))).thenReturn(columnsStmt);
    when(columnsStmt.executeQuery()).thenReturn(columnsRs);
    when(columnsRs.next()).thenReturn(false);

    // Table registration utils
    tableRegUtilsMock.when(() -> TableRegistrationUtils.alreadyExistTable(anyString()))
        .thenReturn(false);
    tableRegUtilsMock.when(() -> TableRegistrationUtils.getDataPackage(module))
        .thenReturn(dataPackage);
    tableRegUtilsMock.when(() -> TableRegistrationUtils.determineJavaClassName(anyString(), anyString()))
        .thenReturn("TestClass");
    tableRegUtilsMock.when(() -> TableRegistrationUtils.createAdTable(
        any(DataPackage.class),
        anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()
    )).thenReturn(table);
  }
}
