package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Unit tests for {@link RegisterWindow}
 */
@ExtendWith(MockitoExtension.class)
class RegisterWindowTest {

  @InjectMocks
  private RegisterWindow registerWindow;

  @Mock
  private OBDal obDal;

  @Mock
  private OBProvider obProvider;

  @Mock
  private OBContext obContext;

  @Mock
  private OBCriteria<ModuleDBPrefix> modulePrefixCriteria;

  @Mock
  private ModuleDBPrefix moduleDBPrefix;

  @Mock
  private Module module;

  @Mock
  private DataPackage dataPackage;

  @Mock
  private Window window;

  @Mock
  private Menu menu;

  @Mock
  private Client client;

  @Mock
  private Organization organization;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;
  private List<DataPackage> dataPackageList;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obProviderMock = mockStatic(OBProvider.class);
    obContextMock = mockStatic(OBContext.class);
    messageMock = mockStatic(OBMessageUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obProviderMock.when(OBProvider::getInstance).thenReturn(obProvider);
    obContextMock.when(OBContext::getOBContext).thenReturn(obContext);

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WindowCreated"))
        .thenReturn("Window '%s' created successfully with ID: %s");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_PrefixNotFound"))
        .thenReturn("Prefix '%s' not found");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ModNotDev"))
        .thenReturn("Module '%s' is not in development");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ModNotDP"))
        .thenReturn("Module '%s' does not have a data package");

    parameters = new HashMap<>();
    responseVars = new HashMap<>();
    dataPackageList = new ArrayList<>();

  }

  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    messageMock.close();
  }

  @Test
  void testGetWithValidParametersShouldCreateWindowAndMenu() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");
    parameters.put("Description", "Test Description");
    parameters.put("HelpComment", "Test Help");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
    assertTrue(responseVars.get("message").contains("Test Window"));

    verify(obDal).save(any(Window.class));
    verify(obDal).save(any(Menu.class));
    verify(obDal).flush();
  }

  @Test
  void testGetWithMissingDBPrefixShouldReturnError() {
    parameters.put("Name", "Test Window");
    parameters.put("Description", "Test Description");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertFalse(responseVars.containsKey("message"));
    assertTrue(responseVars.get("error").contains("prefix cannot be null"));

    verify(obDal).rollbackAndClose();
    verify(obDal, never()).flush();
  }

  @Test
  void testGetWithNullDBPrefixShouldReturnError() {
    parameters.put("DBPrefix", null);
    parameters.put("Name", "Test Window");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing parameter, prefix cannot be null.", responseVars.get("error"));
    verify(obDal).rollbackAndClose();
  }

  @Test
  void testGetWithEmptyDBPrefixShouldReturnError() {
    parameters.put("DBPrefix", "");
    parameters.put("Name", "Test Window");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("prefix cannot be null"));
    verify(obDal).rollbackAndClose();
  }

  @Test
  void testGetWithNonExistentPrefixShouldReturnError() {
    parameters.put("DBPrefix", "NONEXISTENT");
    parameters.put("Name", "Test Window");

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(null);

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("Prefix"));
    assertTrue(responseVars.get("error").contains("not found"));
    verify(obDal).rollbackAndClose();
  }

  @Test
  void testGetWithModuleNotInDevelopmentShouldReturnError() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
    when(moduleDBPrefix.getModule()).thenReturn(module);
    when(module.isInDevelopment()).thenReturn(false);
    when(module.getName()).thenReturn("Test Module");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("not in development"));
    verify(obDal).rollbackAndClose();
  }

  @Test
  void testGetWithModuleWithoutDataPackageShouldReturnError() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
    when(moduleDBPrefix.getModule()).thenReturn(module);
    when(module.isInDevelopment()).thenReturn(true);
    when(module.getDataPackageList()).thenReturn(new ArrayList<>());
    when(module.getName()).thenReturn("Test Module");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("does not have a data package"));
    verify(obDal).rollbackAndClose();
  }

  @Test
  void testGetWithMinimalParametersShouldCreateWindow() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
    verify(obDal).save(any(Window.class));
    verify(obDal).save(any(Menu.class));
    verify(obDal).flush();
  }

  @Test
  void testGetWithAllParametersShouldCreateWindowWithAllFields() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Complete Window");
    parameters.put("Description", "Complete Description");
    parameters.put("HelpComment", "Complete Help Comment");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
    verify(obDal, times(2)).save(any());
    verify(obDal).flush();
  }

  @Test
  void testGetShouldCreateWindowWithCorrectType() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(window).setWindowType(RegisterWindow.WINDOW_TYPE);
    verify(window).setSalesTransaction(true);
  }

  @Test
  void testGetShouldCreateMenuWithCorrectAction() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(menu).setAction(RegisterWindow.MENU_SET_ACTION);
    verify(menu).setSummaryLevel(false);
    verify(menu).setActive(true);
    verify(menu).setOpenlinkinbrowser(false);
  }

  @Test
  void testGetShouldSetWindowAndMenuToSameModule() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(window).setModule(module);
    verify(menu).setModule(module);
  }

  @Test
  void testGetShouldSetMenuWindowReference() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(menu).setWindow(window);
  }

  @Test
  void testGetShouldRollbackOnException() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenThrow(new RuntimeException("Database error"));

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    verify(obDal).rollbackAndClose();
    verify(obDal, never()).flush();
  }

  @Test
  void testGetWithSpecialCharactersInNameShouldProcess() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test-Window_123");
    parameters.put("Description", "Test & Special <Characters>");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
  }

  @Test
  void testGetShouldSetNewOBObjectFlags() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(window).setNewOBObject(true);
    verify(menu).setNewOBObject(true);
  }

  @Test
  void testGetShouldSetClientAndOrganizationFromContext() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    setupSuccessfulScenario();
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(organization);

    registerWindow.get(parameters, responseVars);

    verify(window).setClient(client);
    verify(window).setOrganization(organization);
    verify(menu).setClient(client);
    verify(menu).setOrganization(organization);
  }

  @Test
  void testGetWithLongDescriptionShouldProcess() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");
    parameters.put("Description", "A".repeat(500));
    parameters.put("HelpComment", "B".repeat(500));

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
  }

  @Test
  void testGetShouldUseFirstDataPackageFromList() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");

    DataPackage firstPackage = mock(DataPackage.class);
    DataPackage secondPackage = mock(DataPackage.class);
    dataPackageList.add(firstPackage);
    dataPackageList.add(secondPackage);

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
    when(moduleDBPrefix.getModule()).thenReturn(module);
    when(module.isInDevelopment()).thenReturn(true);
    when(module.getDataPackageList()).thenReturn(dataPackageList);
    when(firstPackage.getModule()).thenReturn(module);

    when(obProvider.get(Window.class)).thenReturn(window);
    when(obProvider.get(Menu.class)).thenReturn(menu);
    when(window.getName()).thenReturn("Test Window");
    when(window.getId()).thenReturn("window123");
    when(window.getModule()).thenReturn(module);

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    verify(module).getDataPackageList();
  }

  @Test
  void testGetShouldNotModifyInputParameters() {
    parameters.put("DBPrefix", "TEST");
    parameters.put("Name", "Test Window");
    Map<String, String> originalParams = new HashMap<>(parameters);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertEquals(originalParams, parameters);
  }

  private void setupSuccessfulScenario() {
    dataPackageList.add(dataPackage);

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
    when(moduleDBPrefix.getModule()).thenReturn(module);
    when(module.isInDevelopment()).thenReturn(true);
    when(module.getDataPackageList()).thenReturn(dataPackageList);
    when(dataPackage.getModule()).thenReturn(module);

    when(obProvider.get(Window.class)).thenReturn(window);
    when(obProvider.get(Menu.class)).thenReturn(menu);
    when(window.getName()).thenReturn("Test Window");
    when(window.getId()).thenReturn("window123");
    when(window.getModule()).thenReturn(module);
  }
}
