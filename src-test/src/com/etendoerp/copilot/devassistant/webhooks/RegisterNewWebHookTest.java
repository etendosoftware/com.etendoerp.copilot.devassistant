package com.etendoerp.copilot.devassistant.webhooks;

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

  @Mock
  private OBDal obDal;

  @Mock
  private OBProvider obProvider;

  @Mock
  private OBContext obContext;

  @Mock
  private Module module;

  @Mock
  private Role role;

  @Mock
  private DefinedWebHook definedWebHook;

  @Mock
  private DefinedWebhookParam webhookParam;

  @Mock
  private DefinedwebhookRole webhookRole;

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

  /**
   * Verifies that a webhook, its parameters and role entry are created and persisted
   * when all required parameters are valid.
   */
  @Test
  void testGetWithValidParametersShouldCreateWebhookSuccessfully() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook '%s' created successfully. %s");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
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
    requestParams.put("SearchKey", "TestWebhook");
    requestParams.put("Params", "param1;param2");
    requestParams.put("ModuleJavaPackage", "com.test.module");

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_MisisngParameters"))
        .thenReturn("Missing required parameters");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing required parameters", responseVars.get("error"));
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned if the SearchKey parameter is missing.
   */
  @Test
  void testGetWithMissingSearchKeyShouldReturnError() {
    requestParams.put("Javaclass", "com.test.MyWebhook");
    requestParams.put("Params", "param1;param2");
    requestParams.put("ModuleJavaPackage", "com.test.module");

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_MisisngParameters"))
        .thenReturn("Missing required parameters");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing required parameters", responseVars.get("error"));
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned if the Params parameter is missing.
   */
  @Test
  void testGetWithMissingParamsShouldReturnError() {
    requestParams.put("Javaclass", "com.test.MyWebhook");
    requestParams.put("SearchKey", "TestWebhook");
    requestParams.put("ModuleJavaPackage", "com.test.module");

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_MisisngParameters"))
        .thenReturn("Missing required parameters");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing required parameters", responseVars.get("error"));
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned if the ModuleJavaPackage parameter is missing.
   */
  @Test
  void testGetWithMissingModuleJavaPackageShouldReturnError() {
    requestParams.put("Javaclass", "com.test.MyWebhook");
    requestParams.put("SearchKey", "TestWebhook");
    requestParams.put("Params", "param1;param2");

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_MisisngParameters"))
        .thenReturn("Missing required parameters");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing required parameters", responseVars.get("error"));
    verify(obDal, never()).save(any());
  }

  /**
   * Ensures an error is returned when required parameters are provided but blank.
   */
  @Test
  void testGetWithBlankParametersShouldReturnError() {
    // Arrange
    requestParams.put("Javaclass", "   ");
    requestParams.put("SearchKey", "   ");
    requestParams.put("Params", "   ");
    requestParams.put("ModuleJavaPackage", "   ");

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_MisisngParameters"))
        .thenReturn("Missing required parameters");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing required parameters", responseVars.get("error"));
  }

  /**
   * Verifies the created DefinedWebHook contains the expected properties.
   */
  @Test
  void testGetShouldCreateWebhookWithCorrectProperties() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(definedWebHook).setNewOBObject(true);
    verify(definedWebHook).setModule(module);
    verify(definedWebHook).setJavaClass("com.test.MyWebhook");
    verify(definedWebHook).setName("TestWebhook");
    verify(definedWebHook).setAllowGroupAccess(true);
  }

  /**
   * Verifies multiple semicolon-separated parameters result in multiple
   * DefinedWebhookParam entities being persisted.
   */
  @Test
  void testGetWithMultipleParamsShouldCreateAllParameters() {
    setupValidRequestParams();
    requestParams.put("Params", "param1;param2;param3");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obDal, times(3)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies a single parameter results in one DefinedWebhookParam persisted.
   */
  @Test
  void testGetWithSingleParamShouldCreateOneParameter() {
    setupValidRequestParams();
    requestParams.put("Params", "singleParam");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obDal, times(1)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies that parameter names are trimmed (no leading/trailing spaces) before persisting.
   */
  @Test
  void testGetWithParamsContainingSpacesShouldTrimParameters() {
    setupValidRequestParams();
    requestParams.put("Params", " param1 ; param2 ; param3 ");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    ArgumentCaptor<DefinedWebhookParam> paramCaptor = ArgumentCaptor.forClass(DefinedWebhookParam.class);

    service.get(requestParams, responseVars);

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
    requestParams.put("Params", "param1;;param2;  ;param3");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obDal, times(3)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies each DefinedWebhookParam is initialized with correct defaults and references.
   */
  @Test
  void testGetShouldSetParameterPropertiesCorrectly() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

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
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();
    when(obContext.getRole()).thenReturn(role);


    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

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

    utilsMock.when(() -> Utils.getModuleByJavaPackage("com.test.module")).thenReturn(null);

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_SetModuleManually"))
        .thenReturn("Please set module manually");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook '%s' created. %s");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertTrue(responseVars.get("message").contains("Please set module manually"));
  }

  /**
   * When the module is resolved, a success message is returned without additional warnings.
   */
  @Test
  void testGetWhenModuleFoundShouldNotIncludeWarningMessage() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook '%s' created. %s");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("message"));
  }

  /**
   * Ensures OBDal.flush() is called after all entities are saved.
   */
  @Test
  void testGetShouldFlushAfterAllSaves() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obDal).flush();
  }

  /**
   * Verifies a long list of parameters produces the expected number of saves.
   */
  @Test
  void testGetWithLongParameterListShouldCreateAllParameters() {
    setupValidRequestParams();
    requestParams.put("Params", "p1;p2;p3;p4;p5;p6;p7;p8;p9;p10");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obDal, times(10)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Ensures parameters with allowed special characters are handled and persisted.
   */
  @Test
  void testGetWithSpecialCharactersInParamsShouldHandleCorrectly() {
    setupValidRequestParams();
    requestParams.put("Params", "param-1;param_2;param.3");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obDal, times(3)).save(any(DefinedWebhookParam.class));
  }

  /**
   * Verifies the webhook is created with allowGroupAccess set to true.
   */
  @Test
  void testGetShouldSetAllowGroupAccessToTrue() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(definedWebHook).setAllowGroupAccess(true);
  }

  /**
   * Verifies the current user's role from OBContext is used for the webhook role entry.
   */
  @Test
  void testGetShouldUseCurrentUserRole() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();
    when(obContext.getRole()).thenReturn(role);

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WebhookCreated"))
        .thenReturn("Webhook created");

    service.get(requestParams, responseVars);

    verify(obContext).getRole();
    verify(webhookRole).setRole(role);
  }

  /**
   * Populates requestParams with a valid set of input values for successful execution.
   */
  private void setupValidRequestParams() {
    requestParams.put("Javaclass", "com.test.MyWebhook");
    requestParams.put("SearchKey", "TestWebhook");
    requestParams.put("Params", "param1;param2");
    requestParams.put("ModuleJavaPackage", "com.test.module");
  }

  /**
   * Configures the mocks for a successful creation flow, including module resolution and
   * entity provisioning via OBProvider.
   */
  private void setupMocksForSuccessfulCreation() {
    utilsMock.when(() -> Utils.getModuleByJavaPackage("com.test.module")).thenReturn(module);

    when(obProvider.get(DefinedWebHook.class)).thenReturn(definedWebHook);
    when(obProvider.get(DefinedWebhookParam.class)).thenReturn(webhookParam);
    when(obProvider.get(DefinedwebhookRole.class)).thenReturn(webhookRole);
  }
}
