package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.client.application.ReportDefinition;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.ui.Menu;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link ProcessDefinitionJasper} covering the HTTP GET entrypoint and the
 * internal factory methods used to create Process Definition, Report Definition and Parameters.
 * <p>
 * These tests use JUnit 5 and Mockito to validate the following behaviors:
 * <ul>
 *   <li>Validation of required request parameters and corresponding error messages.</li>
 *   <li>Validation of module DB prefix existence.</li>
 *   <li>Validation of report path format (must start with "web/" and end with ".jrxml").</li>
 *   <li>Validation of parameters JSON format and reference resolution.</li>
 *   <li>Happy-path creation flow that persists Process, ReportDefinition and optional Parameters.</li>
 * </ul>
 * Static dependencies (OBDal, OBProvider, OBContext, Utils and OBMessageUtils) are mocked to keep
 * the tests isolated from the Openbravo runtime.
 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionJasperTest {

  @InjectMocks
  private ProcessDefinitionJasper service;

  @Mock
  private OBDal obDal;

  @Mock
  private OBProvider obProvider;

  @Mock
  private OBContext obContext;

  @Mock
  private OBCriteria<ModuleDBPrefix> prefixCriteria;

  @Mock
  private OBCriteria<Reference> referenceCriteria;

  @Mock
  private ModuleDBPrefix moduleDBPrefix;

  @Mock
  private Module module;

  @Mock
  private Process process;

  @Mock
  private ReportDefinition reportDefinition;

  @Mock
  private Parameter parameter;

  @Mock
  private Reference reference;

  @Mock
  private Menu menu;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<Utils> utilsMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> requestParams;
  private Map<String, String> responseVars;

  /**
   * Initializes static mocks and fresh request/response maps before each test execution.
   * <p>
   * Mocks OBDal/OBProvider/OBContext singletons and utility/message helpers to avoid hitting the
   * actual Openbravo infrastructure.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obProviderMock = mockStatic(OBProvider.class);
    obContextMock = mockStatic(OBContext.class);
    utilsMock = mockStatic(Utils.class);
    messageMock = mockStatic(OBMessageUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obProviderMock.when(OBProvider::getInstance).thenReturn(obProvider);
    obContextMock.when(OBContext::getOBContext).thenReturn(obContext);

    requestParams = new HashMap<>();
    responseVars = new HashMap<>();
  }

  /**
   * Releases all static mocks after each test to prevent cross-test interference.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    utilsMock.close();
    messageMock.close();
  }

  /**
   * Given valid parameters without custom report parameters, when invoking get, then a process and
   * report definition are created and a success message is returned.
   */
  @Test
  void testGetWithValidParametersShouldCreateProcessSuccessfully() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_RecordCreated"))
        .thenReturn("Record created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Record created successfully", responseVars.get("message"));
    assertFalse(responseVars.containsKey("error"));
    verify(obDal, atLeastOnce()).save(any());
    verify(obDal, atLeastOnce()).flush();
  }

  /**
   * Missing Prefix must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingPrefixShouldReturnError() {
    requestParams.put("SearchKey", "TEST_KEY");
    requestParams.put("ReportName", "Test Report");
    requestParams.put("ReportPath", "web/test/report.jrxml");
    requestParams.put("Parameters", "[]");

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_MissingParameter", new String[]{"Prefix"}))
        .thenReturn("Missing parameter: Prefix");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing parameter: Prefix", responseVars.get("error"));
  }

  /**
   * Missing SearchKey must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingSearchKeyShouldReturnError() {
    requestParams.put("Prefix", "TEST");
    requestParams.put("ReportName", "Test Report");
    requestParams.put("ReportPath", "web/test/report.jrxml");
    requestParams.put("Parameters", "[]");

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_MissingParameter", new String[]{"SearchKey"}))
        .thenReturn("Missing parameter: SearchKey");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing parameter: SearchKey", responseVars.get("error"));
  }

  /**
   * Missing ReportName must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingReportNameShouldReturnError() {
    requestParams.put("Prefix", "TEST");
    requestParams.put("SearchKey", "TEST_KEY");
    requestParams.put("ReportPath", "web/test/report.jrxml");
    requestParams.put("Parameters", "[]");

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_MissingParameter", new String[]{"ReportName"}))
        .thenReturn("Missing parameter: ReportName");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing parameter: ReportName", responseVars.get("error"));
  }

  /**
   * Missing ReportPath must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingReportPathShouldReturnError() {
    requestParams.put("Prefix", "TEST");
    requestParams.put("SearchKey", "TEST_KEY");
    requestParams.put("ReportName", "Test Report");
    requestParams.put("Parameters", "[]");

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_MissingParameter", new String[]{"ReportPath"}))
        .thenReturn("Missing parameter: ReportPath");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing parameter: ReportPath", responseVars.get("error"));
  }

  /**
   * If the provided Prefix does not match any ModuleDBPrefix, an error must be returned.
   */
  @Test
  void testGetWithInvalidPrefixShouldReturnError() {
    setupValidRequestParams();
    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCriteria);
    when(prefixCriteria.add(any())).thenReturn(prefixCriteria);
    when(prefixCriteria.createAlias(anyString(), anyString())).thenReturn(prefixCriteria);
    when(prefixCriteria.setMaxResults(anyInt())).thenReturn(prefixCriteria);
    when(prefixCriteria.uniqueResult()).thenReturn(null);

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("prefix does not exist"));
  }

  /**
   * ReportPath must start with "web/". If not, an incorrect format error is returned.
   */
  @Test
  void testGetWithInvalidPathFormatMissingWebIndicatorShouldReturnError() {
    setupValidRequestParams();
    requestParams.put("ReportPath", "test/report.jrxml");

    setupPrefixValidation();

    messageMock.when(() -> OBMessageUtils.getI18NMessage(eq("COPDEV_IncorrectFormat"), any()))
        .thenReturn("Incorrect format: test/report.jrxml");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Incorrect format: test/report.jrxml", responseVars.get("error"));
  }

  /**
   * ReportPath must end with ".jrxml". If not, an incorrect format error is returned.
   */
  @Test
  void testGetWithInvalidPathFormatMissingExtensionShouldReturnError() {
    setupValidRequestParams();
    requestParams.put("ReportPath", "web/test/report.xml");

    setupPrefixValidation();

    messageMock.when(() -> OBMessageUtils.getI18NMessage(eq("COPDEV_IncorrectFormat"), any()))
        .thenReturn("Incorrect format: web/test/report.xml");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Incorrect format: web/test/report.xml", responseVars.get("error"));
  }

  /**
   * Parameters JSON payload must include all required fields. If not, an error message is returned.
   */
  @Test
  void testGetWithInvalidParametersFormatShouldReturnError() {
    setupValidRequestParams();
    requestParams.put("Parameters", "[{\"BD_NAME\":\"test\",\"NAME\":\"Test\"}]");

    setupPrefixValidation();

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("format is incorrect"));
  }

  /**
   * Verifies that createProcessDefinition builds and persists a Process with the expected
   * attributes and metadata.
   */
  @Test
  void testCreateProcessDefinitionShouldCreateAndReturnProcess() {
    when(obProvider.get(Process.class)).thenReturn(process);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);

    Process result = service.createProcessDefinition(
        "TEST",
        "TEST_KEY",
        "Test Report",
        "Test Description",
        "Test Help"
    );

    // Assert
    assertNotNull(result);
    verify(process).setNewOBObject(true);
    verify(process).setModule(module);
    verify(process).setSearchKey("TEST_KEY");
    verify(process).setName("Test Report");
    verify(process).setDescription("Test Description");
    verify(process).setHelpComment("Test Help");
    verify(process).setUIPattern("OBUIAPP_Report");
    verify(process).setDataAccessLevel("3");
    verify(process).setJavaClassName("org.openbravo.client.application.report.BaseReportActionHandler");
    verify(process).setActive(true);
    verify(obDal).save(process);
    verify(obDal).flush();
  }

  /**
   * Verifies that createReportDefinition builds and persists a ReportDefinition tied to the
   * provided Process.
   */
  @Test
  void testCreateReportDefinitionShouldCreateReportDefinition() {
    when(obProvider.get(ReportDefinition.class)).thenReturn(reportDefinition);

    service.createReportDefinition(process, "web/test/report.jrxml");

    verify(reportDefinition).setActive(true);
    verify(reportDefinition).setProcessDefintion(process);
    verify(reportDefinition).setPDFTemplate("web/test/report.jrxml");
    verify(obDal).save(reportDefinition);
    verify(obDal).flush();
  }

  /**
   * Given valid parameters including Parameters JSON, the flow should create the process, report
   * definition and the corresponding Parameter entities.
   */
  @Test
  void testGetWithValidParametersAndParametersShouldCreateProcessWithParameters() {
    setupValidRequestParamsWithParameters();
    setupMocksForSuccessfulCreationWithParameters();

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_RecordCreated"))
        .thenReturn("Record created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Record created successfully", responseVars.get("message"));
    verify(obProvider, atLeastOnce()).get(Parameter.class);
    verify(obDal, atLeastOnce()).save(any(Parameter.class));
  }

  /**
   * When a parameter's REFERENCE code cannot be resolved, an error must be returned.
   */
  @Test
  void testGetWithNonExistentReferenceShouldReturnError() {
    setupValidRequestParamsWithParameters();
    setupPrefixValidation();

    when(obProvider.get(Process.class)).thenReturn(process);
    when(obProvider.get(ReportDefinition.class)).thenReturn(reportDefinition);
    when(obProvider.get(Parameter.class)).thenReturn(parameter);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);

    when(obDal.createCriteria(Reference.class)).thenReturn(referenceCriteria);
    when(referenceCriteria.add(any())).thenReturn(referenceCriteria);
    when(referenceCriteria.setMaxResults(anyInt())).thenReturn(referenceCriteria);
    when(referenceCriteria.uniqueResult()).thenReturn(null);

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_NullReference"))
        .thenReturn("Reference cannot be null");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Reference cannot be null", responseVars.get("error"));
  }

  /**
   * Populates requestParams with the minimal valid payload without Parameters.
   */
  private void setupValidRequestParams() {
    requestParams.put("Prefix", "TEST");
    requestParams.put("SearchKey", "TEST_KEY");
    requestParams.put("ReportName", "Test Report");
    requestParams.put("Description", "Test Description");
    requestParams.put("HelpComment", "Test Help");
    requestParams.put("ReportPath", "web/test/report.jrxml");
    requestParams.put("Parameters", "[]");
  }

  /**
   * Populates requestParams with a valid payload including a minimal Parameters JSON array.
   */
  private void setupValidRequestParamsWithParameters() {
    setupValidRequestParams();
    requestParams.put("Parameters",
        "[{\"BD_NAME\":\"test\",\"NAME\":\"Test\",\"LENGTH\":\"10\",\"SEQNO\":\"10\",\"REFERENCE\":\"String\"}]");
  }

  /**
   * Stubs the OBDal criteria to return a non-null ModuleDBPrefix for Prefix validation.
   */
  private void setupPrefixValidation() {
    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCriteria);
    when(prefixCriteria.add(any())).thenReturn(prefixCriteria);
    when(prefixCriteria.createAlias(anyString(), anyString())).thenReturn(prefixCriteria);
    when(prefixCriteria.setMaxResults(anyInt())).thenReturn(prefixCriteria);
    when(prefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
  }

  /**
   * Prepares common happy-path stubs for creating Process and ReportDefinition (and Menu if
   * needed), including module resolution by prefix.
   */
  private void setupMocksForSuccessfulCreation() {
    setupPrefixValidation();

    when(obProvider.get(Process.class)).thenReturn(process);
    when(obProvider.get(ReportDefinition.class)).thenReturn(reportDefinition);
    when(obProvider.get(Menu.class)).thenReturn(menu);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
  }

  /**
   * Extends the successful creation stubs to include Parameter creation and Reference resolution.
   */
  private void setupMocksForSuccessfulCreationWithParameters() {
    setupMocksForSuccessfulCreation();

    when(obProvider.get(Parameter.class)).thenReturn(parameter);
    when(obDal.createCriteria(Reference.class)).thenReturn(referenceCriteria);
    when(referenceCriteria.add(any())).thenReturn(referenceCriteria);
    when(referenceCriteria.setMaxResults(anyInt())).thenReturn(referenceCriteria);
    when(referenceCriteria.uniqueResult()).thenReturn(reference);
  }
}
