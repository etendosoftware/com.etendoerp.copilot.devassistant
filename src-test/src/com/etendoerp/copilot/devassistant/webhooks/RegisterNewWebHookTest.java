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

import static com.etendoerp.copilot.devassistant.TestConstants.ERR_MISSING_PARAMETERS;
import static com.etendoerp.copilot.devassistant.TestConstants.JAVA_CLASS;
import static com.etendoerp.copilot.devassistant.TestConstants.JAVA_CLASS_VALUE;
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.MISSING_REQUIRED_PARAMS;
import static com.etendoerp.copilot.devassistant.TestConstants.MODULE_JAVA_PACKAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.MODULE_TEST_PACKAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.PARAMS;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.PARAM_LIST;
import static com.etendoerp.copilot.devassistant.TestConstants.SEARCH_KEY;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_WEBHOOK;
import static com.etendoerp.copilot.devassistant.TestConstants.WEBHOOK_CREATED;
import static com.etendoerp.copilot.devassistant.TestConstants.WEBHOOK_CREATED_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedWebhookParam;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;

/**
 * Unit tests for {@link RegisterNewWebHook} service.
 *
 * <p>This test suite exercises the GET handler that registers a new webhook
 * with its parameters and role assignment based on incoming request params.</p>
 *
 * <p>It covers:</p>
 * <ul>
 *   <li>Successful creation flow (webhook, parameters, role) and flush.</li>
 *   <li>Validation errors for missing/blank required parameters.</li>
 *   <li>Parameter parsing (semicolon-separated, trimming, skipping empties).</li>
 *   <li>Role assignment from current OBContext.</li>
 *   <li>Module resolution by Java package and warning message when not found.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RegisterNewWebHookTest {

  @InjectMocks
  private RegisterNewWebHook service;

  @Mock private OBDal obDal;
  @Mock private OBProvider obProvider;
  @Mock private OBContext obContext;
  @Mock private Module module;
  @Mock private Role role;
  @Mock private DefinedWebHook definedWebHook;
  @Mock private DefinedWebhookParam webhookParam;
  @Mock private DefinedwebhookRole webhookRole;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<Utils> utilsMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> requestParams;
  private Map<String, String> responseVars;

  /**
   * Initializes static mocks and default request/response maps before each test.
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
   * Closes all static mocks after each test to avoid cross-test interference.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    utilsMock.close();
    messageMock.close();
  }

  private void setupSuccessfulFlow() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
  }

  private void executeSuccessfulGet() {
    service.get(requestParams, responseVars);
  }

  private void runSuccessfulFlow() {
    setupSuccessfulFlow();
    executeSuccessfulGet();
  }

  private void runMissingParamFlow() {
    setupMissingParametersMessage();
    service.get(requestParams, responseVars);
  }

  @Test
  void testGetWithValidParametersShouldCreateWebhookSuccessfully() {
    runSuccessfulFlow();

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));

    verify(obDal, atLeastOnce()).save(any(DefinedWebHook.class));
    verify(obDal, atLeastOnce()).save(any(DefinedWebhookParam.class));
    verify(obDal, atLeastOnce()).save(any(DefinedwebhookRole.class));
    verify(obDal).flush();
  }

  /**
   * Ensures an error is returned if the Javaclass parameter is missing.
   */
  @Test
  void testGetWithMissingJavaclassShouldReturnError() {
    setupRequestParamsWithoutParam(JAVA_CLASS);
    runMissingParamFlow();
    assertErrorResponse();
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned if the SearchKey parameter is missing.
   */
  @Test
  void testGetWithMissingSearchKeyShouldReturnError() {
    setupRequestParamsWithoutParam(SEARCH_KEY);
    runMissingParamFlow();
    assertErrorResponse();
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned if the Params parameter is missing.
   */
  @Test
  void testGetWithMissingParamsShouldReturnError() {
    setupRequestParamsWithoutParam(PARAMS);
    runMissingParamFlow();
    assertErrorResponse();
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned if the ModuleJavaPackage parameter is missing.
   */
  @Test
  void testGetWithMissingModuleJavaPackageShouldReturnError() {
    setupRequestParamsWithoutParam(MODULE_JAVA_PACKAGE);
    runMissingParamFlow();
    assertErrorResponse();
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned when required parameters are provided but blank.
   */
  @Test
  void testGetWithBlankParametersShouldReturnError() {
    requestParams.put(JAVA_CLASS, "   ");
    requestParams.put(SEARCH_KEY, "   ");
    requestParams.put(PARAMS, "   ");
    requestParams.put(MODULE_JAVA_PACKAGE, "   ");

    runMissingParamFlow();
    assertErrorResponse();
  }

  /**
   * Verifies the created DefinedWebHook contains the expected properties.
   */
  @Test
  void testGetShouldCreateWebhookWithCorrectProperties() {
    runSuccessfulFlow();

    verify(definedWebHook).setNewOBObject(true);
    verify(definedWebHook).setModule(module);
    verify(definedWebHook).setJavaClass(JAVA_CLASS_VALUE);
    verify(definedWebHook).setName(TEST_WEBHOOK);
    verify(definedWebHook).setAllowGroupAccess(true);
  }

  /**
   * Verifies multiple semicolon-separated parameters result in multiple
   * DefinedWebhookParam entities being persisted.
   */
  @Test
  void testGetWithMultipleParamsShouldCreateAllParameters() {
    setupValidRequestParams();
    requestParams.put(PARAMS, "param1;param2;param3");

    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
    executeSuccessfulGet();

    verify(obDal, times(3)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies a single parameter results in one DefinedWebhookParam persisted.
   */
  @Test
  void testGetWithSingleParamShouldCreateOneParameter() {
    setupValidRequestParams();
    requestParams.put(PARAMS, "singleParam");

    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
    executeSuccessfulGet();

    verify(obDal, times(1)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies that parameter names are trimmed (no leading/trailing spaces) before persisting.
   */
  @Test
  void testGetWithParamsContainingSpacesShouldTrimParameters() {
    setupValidRequestParams();
    requestParams.put(PARAMS, " param1 ; param2 ; param3 ");

    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
    ArgumentCaptor<DefinedWebhookParam> paramCaptor =
        ArgumentCaptor.forClass(DefinedWebhookParam.class);

    executeSuccessfulGet();

    verify(obDal, times(3)).save(paramCaptor.capture());
    verify(webhookParam, times(3)).setName(argThat(name ->
        !name.startsWith(" ") && !name.endsWith(" ")));
  }

  /**
   * Verifies empty entries in the parameter list (e.g., double semicolons) are ignored.
   */
  @Test
  void testGetWithEmptyParamsBetweenSemicolonsShouldSkipEmpty() {
    setupValidRequestParams();
    requestParams.put(PARAMS, "param1;;param2;  ;param3");

    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
    executeSuccessfulGet();

    verify(obDal, times(3)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies each DefinedWebhookParam is initialized with correct defaults and references.
   */
  @Test
  void testGetShouldSetParameterPropertiesCorrectly() {
    runSuccessfulFlow();

    verify(webhookParam, times(2)).setNewOBObject(true);
    verify(webhookParam, times(2)).setModule(module);
    verify(webhookParam, times(2)).setSmfwheDefinedwebhook(definedWebHook);
    verify(webhookParam, times(2)).setRequired(true);
  }

  /**
   * Verifies that a DefinedwebhookRole is created with the current user's role and
   * proper references to webhook and module.
   */
  @Test
  void testGetShouldCreateWebhookRoleWithCorrectProperties() {
    setupSuccessfulFlow();
    when(obContext.getRole()).thenReturn(role);
    executeSuccessfulGet();

    verify(webhookRole).setNewOBObject(true);
    verify(webhookRole).setModuleID(module);
    verify(webhookRole).setSmfwheDefinedwebhook(definedWebHook);
    verify(webhookRole).setRole(role);
  }

  /**
   * When the module cannot be resolved, the success message should include a warning
   * to set the module manually.
   */
  @Test
  void testGetWhenModuleNotFoundShouldIncludeWarningMessage() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    utilsMock.when(() -> Utils.getModuleByJavaPackage(MODULE_TEST_PACKAGE)).thenReturn(null);
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_SetModuleManually"))
        .thenReturn("Please set module manually");
    messageMock.when(() -> OBMessageUtils.messageBD(WEBHOOK_CREATED))
        .thenReturn("Webhook '%s' created. %s");

    executeSuccessfulGet();

    assertTrue(responseVars.containsKey(MESSAGE));
    assertTrue(responseVars.get(MESSAGE).contains("Please set module manually"));
  }

  /**
   * When the module is resolved, a success message is returned without additional warnings.
   */
  @Test
  void testGetWhenModuleFoundShouldNotIncludeWarningMessage() {
    runSuccessfulFlow();
    assertTrue(responseVars.containsKey(MESSAGE));
  }

  /**
   * Ensures OBDal.flush() is called after all entities are saved.
   */
  @Test
  void testGetShouldFlushAfterAllSaves() {
    runSuccessfulFlow();
    verify(obDal).flush();
  }

  /**
   * Verifies a long list of parameters produces the expected number of saves.
   */
  @Test
  void testGetWithLongParameterListShouldCreateAllParameters() {
    setupValidRequestParams();
    requestParams.put(PARAMS, "p1;p2;p3;p4;p5;p6;p7;p8;p9;p10");

    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
    executeSuccessfulGet();

    verify(obDal, times(10)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Ensures parameters with allowed special characters are handled and persisted.
   */
  @Test
  void testGetWithSpecialCharactersInParamsShouldHandleCorrectly() {
    setupValidRequestParams();
    requestParams.put(PARAMS, "param-1;param_2;param.3");

    setupMocksForSuccessfulCreation();
    setupWebhookCreatedMessage();
    executeSuccessfulGet();

    verify(obDal, times(3)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies the webhook is created with allowGroupAccess set to true.
   */
  @Test
  void testGetShouldSetAllowGroupAccessToTrue() {
    runSuccessfulFlow();
    verify(definedWebHook).setAllowGroupAccess(true);
  }

  /**
   * Verifies the current user's role from OBContext is used for the webhook role entry.
   */
  @Test
  void testGetShouldUseCurrentUserRole() {
    setupSuccessfulFlow();
    when(obContext.getRole()).thenReturn(role);
    executeSuccessfulGet();

    verify(obContext).getRole();
    verify(webhookRole).setRole(role);
  }

  /**
   * Populates requestParams with a valid set of input values for successful execution.
   */
  private void setupValidRequestParams() {
    requestParams.put(JAVA_CLASS, JAVA_CLASS_VALUE);
    requestParams.put(SEARCH_KEY, TEST_WEBHOOK);
    requestParams.put(PARAMS, PARAM_LIST);
    requestParams.put(MODULE_JAVA_PACKAGE, MODULE_TEST_PACKAGE);
  }

  /**
   * Populates requestParams excluding one parameter to test missing parameter scenarios.
   *
   * @param excludedParam the parameter key to exclude
   */
  private void setupRequestParamsWithoutParam(String excludedParam) {
    if (!JAVA_CLASS.equals(excludedParam)) {
      requestParams.put(JAVA_CLASS, JAVA_CLASS_VALUE);
    }
    if (!SEARCH_KEY.equals(excludedParam)) {
      requestParams.put(SEARCH_KEY, TEST_WEBHOOK);
    }
    if (!PARAMS.equals(excludedParam)) {
      requestParams.put(PARAMS, PARAM_LIST);
    }
    if (!MODULE_JAVA_PACKAGE.equals(excludedParam)) {
      requestParams.put(MODULE_JAVA_PACKAGE, MODULE_TEST_PACKAGE);
    }
  }

  /**
   * Configures the mocks for a successful creation flow, including module resolution and
   * entity provisioning via OBProvider.
   */
  private void setupMocksForSuccessfulCreation() {
    utilsMock.when(() -> Utils.getModuleByJavaPackage(MODULE_TEST_PACKAGE))
        .thenReturn(module);

    when(obProvider.get(DefinedWebHook.class)).thenReturn(definedWebHook);
    when(obProvider.get(DefinedWebhookParam.class)).thenReturn(webhookParam);
    when(obProvider.get(DefinedwebhookRole.class)).thenReturn(webhookRole);
  }

  /**
   * Sets up the mock for the webhook created success message.
   */
  private void setupWebhookCreatedMessage() {
    messageMock.when(() -> OBMessageUtils.messageBD(WEBHOOK_CREATED))
        .thenReturn(WEBHOOK_CREATED_MESSAGE);
  }

  /**
   * Sets up the mock for the missing parameters error message.
   */
  private void setupMissingParametersMessage() {
    messageMock.when(() -> OBMessageUtils.messageBD(ERR_MISSING_PARAMETERS))
        .thenReturn(MISSING_REQUIRED_PARAMS);
  }

  /**
   * Asserts that an error response is present in responseVars.
   */
  private void assertErrorResponse() {
    assertTrue(responseVars.containsKey(ERROR));
    assertEquals(MISSING_REQUIRED_PARAMS, responseVars.get(ERROR));
  }
}
