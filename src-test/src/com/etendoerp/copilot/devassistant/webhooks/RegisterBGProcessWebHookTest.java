package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for RegisterBGProcessWebHook.
 * <p>
 * These tests validate:
 * <ul>
 *   <li>Happy-path GET invocation registering a background process and returning a success message.</li>
 *   <li>Error handling when DAL criteria evaluation throws an exception, returning an error in the response map.</li>
 *   <li>Correct population and persistence of the AD Process entity through the private createAdProcess method.</li>
 *   <li>Module lookup logic performed by the private static getModule method.</li>
 * </ul>
 * <p>
 * Mockito is used to isolate dependencies such as OBDal, OBProvider, OBContext and utility classes.
 */
@ExtendWith(MockitoExtension.class)
class RegisterBGProcessWebHookTest {

  @InjectMocks
  private RegisterBGProcessWebHook service;

  @Mock
  private OBDal obDal;

  @Mock
  private OBProvider obProvider;

  @Mock
  private OBContext obContext;

  @Mock
  private OBCriteria<Module> moduleCriteria;

  @Mock
  private Module module;

  @Mock
  private Process process;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<OBMessageUtils> obMessageMock;
  private MockedStatic<Utils> utilsMock;

  private Map<String, String> params;
  private Map<String, String> responseVars;

  /**
   * Initializes static mocks and fresh parameter/response maps prior to each test execution.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obProviderMock = mockStatic(OBProvider.class);
    obContextMock = mockStatic(OBContext.class);
    obMessageMock = mockStatic(OBMessageUtils.class);
    utilsMock = mockStatic(Utils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obProviderMock.when(OBProvider::getInstance).thenReturn(obProvider);
    obContextMock.when(OBContext::getOBContext).thenReturn(obContext);

    params = new HashMap<>();
    responseVars = new HashMap<>();
  }

  /**
   * Closes static mocks to avoid cross-test interference and memory leaks.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    obMessageMock.close();
    utilsMock.close();
  }

  /**
   * Verifies that calling get with valid parameters registers a background process and
   * returns a localized success message in the response map.
   */
  @Test
  void testGetWithValidParametersShouldRegisterProcessSuccessfully() {
    params.put("Javapackage", "com.test.module");
    params.put("Name", "TestProcess");
    params.put("SearchKey", "TEST_KEY");
    params.put("Description", "Test description");
    params.put("Help", "Help text");
    params.put("PreventConcurrent", "true");

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.setMaxResults(anyInt())).thenReturn(moduleCriteria);
    when(moduleCriteria.uniqueResult()).thenReturn(module);

    when(obProvider.get(Process.class)).thenReturn(process);
    obMessageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ProcessRegisterSuccessfully"))
        .thenReturn("Process registered successfully");

    service.get(params, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertEquals("Process registered successfully", responseVars.get("message"));
    verify(obDal).save(process);
    verify(obDal).flush();
  }

  /**
   * Ensures that exceptions thrown while resolving the Module via DAL are captured and
   * surfaced in the response map as an error entry.
   */
  @Test
  void testGetWithExceptionShouldReturnError() {
    params.put("Javapackage", "com.error.module");
    params.put("Name", "ErrorProcess");

    when(obDal.createCriteria(Module.class)).thenThrow(new RuntimeException("Database error"));

    service.get(params, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Database error", responseVars.get("error"));
  }

  /**
   * Uses reflection to invoke the private createAdProcess method and asserts that the
   * created Process entity is populated with the expected values and persisted via OBDal.
   *
   * @throws Exception if reflection access or invocation fails
   */
  @Test
  void testCreateAdProcessShouldSetCorrectFieldsAndPersist() throws Exception {
    when(obProvider.get(Process.class)).thenReturn(process);
    when(OBContext.getOBContext()).thenReturn(obContext);
    when(obContext.getCurrentClient()).thenReturn(null);
    when(obContext.getCurrentOrganization()).thenReturn(null);

    var method = RegisterBGProcessWebHook.class.getDeclaredMethod(
        "createAdProcess",
        Module.class, String.class, String.class, String.class,
        String.class, String.class, boolean.class, String.class
    );
    method.setAccessible(true);

    Process result = (Process) method.invoke(
        service,
        module,
        "Name",
        "Desc",
        "Help",
        "com.test.Process",
        "KEY",
        true,
        "7"
    );

    assertNotNull(result);
    verify(process).setModule(module);
    verify(process).setName("Name");
    verify(process).setSearchKey("KEY");
    verify(process).setJavaClassName("com.test.Process");
    verify(process).setBackground(true);
    verify(process).setPreventConcurrentExecutions(true);
    verify(obDal).save(process);
    verify(obDal).flush();
  }

  /**
   * Validates that the private static getModule method returns the Module obtained from
   * the DAL criteria when provided with a package name.
   */
  @Test
  void testGetModuleShouldReturnModuleFromCriteria() {
    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.setMaxResults(anyInt())).thenReturn(moduleCriteria);
    when(moduleCriteria.uniqueResult()).thenReturn(module);

    Module result = invokeGetModule("com.test.module");
    assertEquals(module, result);
  }

  /**
   * Helper to call the private static getModule method via reflection, failing the test on exceptions.
   *
   * @param pkg Java package name used to locate the owning Module
   * @return the resolved Module instance
   */
  private Module invokeGetModule(String pkg) {
    try {
      var method = RegisterBGProcessWebHook.class.getDeclaredMethod("getModule", String.class);
      method.setAccessible(true);
      return (Module) method.invoke(null, pkg);
    } catch (Exception e) {
      fail(e);
      return null;
    }
  }
}
