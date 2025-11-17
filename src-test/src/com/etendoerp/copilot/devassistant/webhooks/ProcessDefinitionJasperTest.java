/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.TestConstants.ERR_MISSING_PARAMETER;
import static com.etendoerp.copilot.devassistant.TestConstants.INCORRECT_FORMAT_NO_JRXML;
import static com.etendoerp.copilot.devassistant.TestConstants.INCORRECT_FORMAT_NO_WEB;
import static com.etendoerp.copilot.devassistant.TestConstants.MISSING_PARAM_PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.MISSING_PARAM_REPORT_NAME;
import static com.etendoerp.copilot.devassistant.TestConstants.MISSING_PARAM_REPORT_PATH;
import static com.etendoerp.copilot.devassistant.TestConstants.MISSING_PARAM_SEARCH_KEY;
import static com.etendoerp.copilot.devassistant.TestConstants.PARAMETERS;
import static com.etendoerp.copilot.devassistant.TestConstants.PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.RECORD_CREATED_SUCCESS;
import static com.etendoerp.copilot.devassistant.TestConstants.REFERENCE_NULL_ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.REPORT_NAME;
import static com.etendoerp.copilot.devassistant.TestConstants.REPORT_PATH;
import static com.etendoerp.copilot.devassistant.TestConstants.REPORT_PATH_VALUE;
import static com.etendoerp.copilot.devassistant.TestConstants.SEARCH_KEY;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_HELP;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_KEY;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_REPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    setupRecordCreatedMessage();

    service.get(requestParams, responseVars);

    assertSuccessResponse();
    verify(obDal, atLeastOnce()).save(any());
    verify(obDal, atLeastOnce()).flush();
  }

  /**
   * Missing Prefix must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingPrefixShouldReturnError() {
    setupRequestParamsWithoutParam(PREFIX);
    setupMissingParameterMessage(PREFIX, MISSING_PARAM_PREFIX);

    service.get(requestParams, responseVars);

    assertErrorResponse(MISSING_PARAM_PREFIX);
  }

  /**
   * Missing SearchKey must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingSearchKeyShouldReturnError() {
    setupRequestParamsWithoutParam(SEARCH_KEY);
    setupMissingParameterMessage(SEARCH_KEY, MISSING_PARAM_SEARCH_KEY);

    service.get(requestParams, responseVars);

    assertErrorResponse(MISSING_PARAM_SEARCH_KEY);
  }

  /**
   * Missing ReportName must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingReportNameShouldReturnError() {
    setupRequestParamsWithoutParam(REPORT_NAME);
    setupMissingParameterMessage(REPORT_NAME, MISSING_PARAM_REPORT_NAME);

    service.get(requestParams, responseVars);

    assertErrorResponse(MISSING_PARAM_REPORT_NAME);
  }

  /**
   * Missing ReportPath must be reported as an error with the translated message.
   */
  @Test
  void testGetWithMissingReportPathShouldReturnError() {
    setupRequestParamsWithoutParam(REPORT_PATH);
    setupMissingParameterMessage(REPORT_PATH, MISSING_PARAM_REPORT_PATH);

    service.get(requestParams, responseVars);

    assertErrorResponse(MISSING_PARAM_REPORT_PATH);
  }

  /**
   * If the provided Prefix does not match any ModuleDBPrefix, an error must be returned.
   */
  @Test
  void testGetWithInvalidPrefixShouldReturnError() {
    setupValidRequestParams();
    setupPrefixCriteriaReturningNull();

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("prefix does not exist"));
  }

  /**
   * ReportPath must start with "web/". If not, an incorrect format error is returned.
   */
  @Test
  void testGetWithInvalidPathFormatMissingWebIndicatorShouldReturnError() {
    setupValidRequestParams();
    requestParams.put(REPORT_PATH, "test/report.jrxml");
    setupPrefixValidation();
    setupIncorrectFormatMessage("test/report.jrxml", INCORRECT_FORMAT_NO_WEB);

    service.get(requestParams, responseVars);

    assertErrorResponse(INCORRECT_FORMAT_NO_WEB);
  }

  /**
   * ReportPath must end with ".jrxml". If not, an incorrect format error is returned.
   */
  @Test
  void testGetWithInvalidPathFormatMissingExtensionShouldReturnError() {
    setupValidRequestParams();
    requestParams.put(REPORT_PATH, "web/test/report.xml");
    setupPrefixValidation();
    setupIncorrectFormatMessage("web/test/report.xml", INCORRECT_FORMAT_NO_JRXML);

    service.get(requestParams, responseVars);

    assertErrorResponse(INCORRECT_FORMAT_NO_JRXML);
  }

  /**
   * Parameters JSON payload must include all required fields. If not, an error message is returned.
   */
  @Test
  void testGetWithInvalidParametersFormatShouldReturnError() {
    setupValidRequestParams();
    requestParams.put(PARAMETERS, "[{\"BD_NAME\":\"test\",\"NAME\":\"Test\"}]");
    setupPrefixValidation();

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("format is incorrect"));
  }

  /**
   * Verifies that createProcessDefinition builds and persists a Process with the expected
   * attributes and metadata.
   */
  @Test
  void testCreateProcessDefinitionShouldCreateAndReturnProcess() {
    when(obProvider.get(Process.class)).thenReturn(process);
    utilsMock.when(() -> Utils.getModuleByPrefix(TEST_PREFIX)).thenReturn(module);

    Process result = service.createProcessDefinition(
        TEST_PREFIX,
        TEST_KEY,
        TEST_REPORT,
        TEST_DESCRIPTION,
        TEST_HELP
    );

    assertNotNull(result);
    verifyProcessCreation();
  }

  /**
   * Verifies that createReportDefinition builds and persists a ReportDefinition tied to the
   * provided Process.
   */
  @Test
  void testCreateReportDefinitionShouldCreateReportDefinition() {
    when(obProvider.get(ReportDefinition.class)).thenReturn(reportDefinition);

    service.createReportDefinition(process, REPORT_PATH_VALUE);

    verifyReportDefinitionCreation();
  }

  /**
   * Given valid parameters including Parameters JSON, the flow should create the process, report
   * definition and the corresponding Parameter entities.
   */
  @Test
  void testGetWithValidParametersAndParametersShouldCreateProcessWithParameters() {
    setupValidRequestParamsWithParameters();
    setupMocksForSuccessfulCreationWithParameters();
    setupRecordCreatedMessage();

    service.get(requestParams, responseVars);

    assertSuccessResponse();
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
    utilsMock.when(() -> Utils.getModuleByPrefix(TEST_PREFIX)).thenReturn(module);

    when(obDal.createCriteria(Reference.class)).thenReturn(referenceCriteria);
    when(referenceCriteria.add(any())).thenReturn(referenceCriteria);
    when(referenceCriteria.setMaxResults(anyInt())).thenReturn(referenceCriteria);
    when(referenceCriteria.uniqueResult()).thenReturn(null);

    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_NullReference"))
        .thenReturn(REFERENCE_NULL_ERROR);

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals(REFERENCE_NULL_ERROR, responseVars.get(ERROR));
  }

  /**
   * Populates requestParams with the minimal valid payload without Parameters.
   */
  private void setupValidRequestParams() {
    requestParams.put(PREFIX, TEST_PREFIX);
    requestParams.put(SEARCH_KEY, TEST_KEY);
    requestParams.put(REPORT_NAME, TEST_REPORT);
    requestParams.put("Description", TEST_DESCRIPTION);
    requestParams.put("HelpComment", TEST_HELP);
    requestParams.put(REPORT_PATH, REPORT_PATH_VALUE);
    requestParams.put(PARAMETERS, "[]");
  }

  /**
   * Populates requestParams excluding one parameter to test missing parameter scenarios.
   *
   * @param excludedParam the parameter key to exclude
   */
  private void setupRequestParamsWithoutParam(String excludedParam) {
    if (!PREFIX.equals(excludedParam)) {
      requestParams.put(PREFIX, TEST_PREFIX);
    }
    if (!SEARCH_KEY.equals(excludedParam)) {
      requestParams.put(SEARCH_KEY, TEST_KEY);
    }
    if (!REPORT_NAME.equals(excludedParam)) {
      requestParams.put(REPORT_NAME, TEST_REPORT);
    }
    if (!REPORT_PATH.equals(excludedParam)) {
      requestParams.put(REPORT_PATH, REPORT_PATH_VALUE);
    }
    requestParams.put(PARAMETERS, "[]");
  }

  /**
   * Populates requestParams with a valid payload including a minimal Parameters JSON array.
   */
  private void setupValidRequestParamsWithParameters() {
    setupValidRequestParams();
    requestParams.put(PARAMETERS,
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
   * Stubs the OBDal prefix criteria to return null (invalid prefix).
   */
  private void setupPrefixCriteriaReturningNull() {
    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCriteria);
    when(prefixCriteria.add(any())).thenReturn(prefixCriteria);
    when(prefixCriteria.createAlias(anyString(), anyString())).thenReturn(prefixCriteria);
    when(prefixCriteria.setMaxResults(anyInt())).thenReturn(prefixCriteria);
    when(prefixCriteria.uniqueResult()).thenReturn(null);
  }

  /**
   * Prepares common happy-path stubs for creating Process and ReportDefinition (and Menu if
   * needed), including module resolution by prefix.
   */
  private void setupMocksForSuccessfulCreation() {
    setupPrefixValidation();
    setupBasicMocksForCreation();
  }

  /**
   * Sets up basic mocks needed for entity creation.
   */
  private void setupBasicMocksForCreation() {
    when(obProvider.get(Process.class)).thenReturn(process);
    when(obProvider.get(ReportDefinition.class)).thenReturn(reportDefinition);
    when(obProvider.get(Menu.class)).thenReturn(menu);
    utilsMock.when(() -> Utils.getModuleByPrefix(TEST_PREFIX)).thenReturn(module);
  }

  /**
   * Extends the successful creation stubs to include Parameter creation and Reference resolution.
   */
  private void setupMocksForSuccessfulCreationWithParameters() {
    setupMocksForSuccessfulCreation();
    when(obProvider.get(Parameter.class)).thenReturn(parameter);
    setupReferenceCriteria();
  }

  /**
   * Sets up reference criteria to return a valid reference.
   */
  private void setupReferenceCriteria() {
    when(obDal.createCriteria(Reference.class)).thenReturn(referenceCriteria);
    when(referenceCriteria.add(any())).thenReturn(referenceCriteria);
    when(referenceCriteria.setMaxResults(anyInt())).thenReturn(referenceCriteria);
    when(referenceCriteria.uniqueResult()).thenReturn(reference);
  }

  /**
   * Sets up the mock for the record created success message.
   */
  private void setupRecordCreatedMessage() {
    messageMock.when(() -> OBMessageUtils.getI18NMessage("COPDEV_RecordCreated"))
        .thenReturn(RECORD_CREATED_SUCCESS);
  }

  /**
   * Sets up the mock for a missing parameter error message.
   *
   * @param paramName the parameter name
   * @param errorMessage the expected error message
   */
  private void setupMissingParameterMessage(String paramName, String errorMessage) {
    messageMock.when(() -> OBMessageUtils.getI18NMessage(ERR_MISSING_PARAMETER, new String[]{paramName}))
        .thenReturn(errorMessage);
  }

  /**
   * Sets up the mock for an incorrect format error message.
   *
   * @param path the invalid path
   * @param errorMessage the expected error message
   */
  private void setupIncorrectFormatMessage(String path, String errorMessage) {
    messageMock.when(() -> OBMessageUtils.getI18NMessage(
        "COPDEV_IncorrectFormat",
        new String[]{path}
    )).thenReturn(errorMessage);
  }

  /**
   * Asserts that a success response is present in responseVars.
   */
  private void assertSuccessResponse() {
    assertEquals(RECORD_CREATED_SUCCESS, responseVars.get("message"));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Asserts that an error response is present in responseVars with the expected message.
   *
   * @param expectedError the expected error message
   */
  private void assertErrorResponse(String expectedError) {
    assertTrue(responseVars.containsKey(ERROR));
    assertEquals(expectedError, responseVars.get(ERROR));
  }

  /**
   * Verifies that the process was created with the correct properties.
   */
  private void verifyProcessCreation() {
    verify(process).setNewOBObject(true);
    verify(process).setModule(module);
    verify(process).setSearchKey(TEST_KEY);
    verify(process).setName(TEST_REPORT);
    verify(process).setDescription(TEST_DESCRIPTION);
    verify(process).setHelpComment(TEST_HELP);
    verify(process).setUIPattern("OBUIAPP_Report");
    verify(process).setDataAccessLevel("3");
    verify(process).setJavaClassName("org.openbravo.client.application.report.BaseReportActionHandler");
    verify(process).setActive(true);
    verify(obDal).save(process);
    verify(obDal).flush();
  }

  /**
   * Verifies that the report definition was created with the correct properties.
   */
  private void verifyReportDefinitionCreation() {
    verify(reportDefinition).setActive(true);
    verify(reportDefinition).setProcessDefintion(process);
    verify(reportDefinition).setPDFTemplate(REPORT_PATH_VALUE);
    verify(obDal).save(reportDefinition);
    verify(obDal).flush();
  }
}
