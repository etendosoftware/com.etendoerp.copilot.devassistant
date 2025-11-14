package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.TestConstants.DB_PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.HELP_COMMENT;
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_WINDOW;
import static com.etendoerp.copilot.devassistant.TestConstants.WINDOW_123;
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
 * Unit tests for {@link RegisterWindow}.
 *
 * <p>This suite validates the behavior of the RegisterWindow.get method, ensuring that:
 * <ul>
 *   <li>Input parameter validation is enforced (DB Prefix, Name, Description, Help).</li>
 *   <li>Modules are correctly resolved through DB prefix lookup via ModuleDBPrefix.</li>
 *   <li>Only modules in development mode are accepted.</li>
 *   <li>Modules must contain at least one associated DataPackage.</li>
 *   <li>Window and Menu entities are correctly instantiated, initialized, linked and persisted.</li>
 *   <li>Client and Organization values are taken from the current OBContext.</li>
 *   <li>Error cases properly trigger rollback and populate the responseVars "error" key.</li>
 *   <li>Success cases populate the "message" key and flush changes successfully.</li>
 *   <li>Special characters, long descriptions and edge-case inputs are handled gracefully.</li>
 *   <li>The method under test does not mutate the original input parameters map.</li>
 * </ul>
 *
 * <p>Static components (OBDal, OBProvider, OBContext, OBMessageUtils) are mocked
 * to fully isolate the logic from the database and the DAL infrastructure.
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

  /**
   * Initializes static mocks (OBDal, OBProvider, OBContext, OBMessageUtils)
   * and prepares the request/response maps before each test.
   * <p>
   * Ensures a clean and isolated testing environment by providing:
   * <ul>
   *   <li>A mocked DAL context</li>
   *   <li>Mocked providers for creating Window and Menu instances</li>
   *   <li>Mocked message translations for predictable outputs</li>
   * </ul>
   */
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

  /**
   * Closes all static mocks after each test execution to avoid
   * cross-test interference and maintain full mock isolation.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    messageMock.close();
  }

  /**
   * Verifies that providing all required parameters results in:
   * - Successful creation of a Window and its associated Menu.
   * - Proper persistence through save() and flush().
   * - A success message being returned in responseVars.
   */
  @Test
  void testGetWithValidParametersShouldCreateWindowAndMenu() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);
    parameters.put(DESCRIPTION, "Test Description");
    parameters.put(HELP_COMMENT, "Test Help");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(MESSAGE).contains(TEST_WINDOW));

    verify(obDal).save(any(Window.class));
    verify(obDal).save(any(Menu.class));
    verify(obDal).flush();
  }

  /**
   * Ensures that if the DB Prefix parameter is missing,
   * the service returns a meaningful error message and performs rollback.
   */
  @Test
  void testGetWithMissingDBPrefixShouldReturnError() {
    parameters.put("Name", TEST_WINDOW);
    parameters.put(DESCRIPTION, "Test Description");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertFalse(responseVars.containsKey(MESSAGE));
    assertTrue(responseVars.get(ERROR).contains("prefix cannot be null"));

    verify(obDal).rollbackAndClose();
    verify(obDal, never()).flush();
  }

  /**
   * Ensures null DB Prefix values are treated as missing,
   * producing an error response and rolling back the DAL transaction.
   */
  @Test
  void testGetWithNullDBPrefixShouldReturnError() {
    parameters.put(DB_PREFIX, null);
    parameters.put("Name", TEST_WINDOW);

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Missing parameter, prefix cannot be null.", responseVars.get(ERROR));
    verify(obDal).rollbackAndClose();
  }

  /**
   * Ensures an empty DB Prefix string is treated as invalid,
   * triggering an error response and a rollback operation.
   */
  @Test
  void testGetWithEmptyDBPrefixShouldReturnError() {
    parameters.put(DB_PREFIX, "");
    parameters.put("Name", TEST_WINDOW);

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("prefix cannot be null"));
    verify(obDal).rollbackAndClose();
  }

  /**
   * Validates that when the DB Prefix cannot be resolved to a ModuleDBPrefix entry,
   * the service returns a 'prefix not found' error and rolls back the transaction.
   */
  @Test
  void testGetWithNonExistentPrefixShouldReturnError() {
    parameters.put(DB_PREFIX, "NONEXISTENT");
    parameters.put("Name", TEST_WINDOW);

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(null);

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Prefix"));
    assertTrue(responseVars.get(ERROR).contains("not found"));
    verify(obDal).rollbackAndClose();
  }

  /**
   * Ensures modules not flagged as 'in development' are rejected,
   * returning a specific error and preventing entity creation.
   */
  @Test
  void testGetWithModuleNotInDevelopmentShouldReturnError() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
    when(moduleDBPrefix.getModule()).thenReturn(module);
    when(module.isInDevelopment()).thenReturn(false);
    when(module.getName()).thenReturn("Test Module");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("not in development"));
    verify(obDal).rollbackAndClose();
  }

  /**
   * Confirms that modules with no associated DataPackage entries
   * are invalid for window creation and produce the corresponding error.
   */
  @Test
  void testGetWithModuleWithoutDataPackageShouldReturnError() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.add(any())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.setMaxResults(anyInt())).thenReturn(modulePrefixCriteria);
    when(modulePrefixCriteria.uniqueResult()).thenReturn(moduleDBPrefix);
    when(moduleDBPrefix.getModule()).thenReturn(module);
    when(module.isInDevelopment()).thenReturn(true);
    when(module.getDataPackageList()).thenReturn(new ArrayList<>());
    when(module.getName()).thenReturn("Test Module");

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("does not have a data package"));
    verify(obDal).rollbackAndClose();
  }

  /**
   * Verifies that only required parameters (DB Prefix and Name)
   * are enough to successfully create a Window and a Menu.
   */
  @Test
  void testGetWithMinimalParametersShouldCreateWindow() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal).save(any(Window.class));
    verify(obDal).save(any(Menu.class));
    verify(obDal).flush();
  }

  /**
   * Ensures the service uses all provided parameters (Name, Description, Help)
   * when creating the Window and persists all fields correctly.
   */
  @Test
  void testGetWithAllParametersShouldCreateWindowWithAllFields() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", "Complete Window");
    parameters.put(DESCRIPTION, "Complete Description");
    parameters.put(HELP_COMMENT, "Complete Help Comment");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal, times(2)).save(any());
    verify(obDal).flush();
  }

  /**
   * Confirms that the newly created Window is assigned:
   * - The correct Window type.
   * - Sales transaction flag set to true.
   */
  @Test
  void testGetShouldCreateWindowWithCorrectType() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(window).setWindowType(RegisterWindow.WINDOW_TYPE);
    verify(window).setSalesTransaction(true);
  }

  /**
   * Validates that the created Menu entity:
   * - Uses the correct action value.
   * - Has summaryLevel=false, active=true, openlinkinbrowser=false.
   */
  @Test
  void testGetShouldCreateMenuWithCorrectAction() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(menu).setAction(RegisterWindow.MENU_SET_ACTION);
    verify(menu).setSummaryLevel(false);
    verify(menu).setActive(true);
    verify(menu).setOpenlinkinbrowser(false);
  }

  /**
   * Ensures both the Window and the Menu are assigned to the same module
   * resolved from the DB Prefix.
   */
  @Test
  void testGetShouldSetWindowAndMenuToSameModule() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(window).setModule(module);
    verify(menu).setModule(module);
  }

  /**
   * Confirms the Menu correctly references the newly created Window entity.
   */
  @Test
  void testGetShouldSetMenuWindowReference() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(menu).setWindow(window);
  }

  /**
   * Ensures that any exception thrown during the process triggers:
   * - A rollbackAndClose() call.
   * - An appropriate error message in responseVars.
   * - No flush() invocation.
   */
  @Test
  void testGetShouldRollbackOnException() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    when(obDal.createCriteria(ModuleDBPrefix.class)).thenThrow(new RuntimeException("Database error"));

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    verify(obDal).rollbackAndClose();
    verify(obDal, never()).flush();
  }

  /**
   * Verifies that Window creation succeeds even when the Name or Description
   * contains special characters, ensuring safe handling of such inputs.
   */
  @Test
  void testGetWithSpecialCharactersInNameShouldProcess() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", "Test-Window_123");
    parameters.put(DESCRIPTION, "Test & Special <Characters>");

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures the Window and Menu objects are flagged as new OBObjects,
   * which is required for correct DAL persistence.
   */
  @Test
  void testGetShouldSetNewOBObjectFlags() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    verify(window).setNewOBObject(true);
    verify(menu).setNewOBObject(true);
  }

  /**
   * Validates that Window and Menu inherit Client and Organization values
   * from the current OBContext.
   */
  @Test
  void testGetShouldSetClientAndOrganizationFromContext() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

    setupSuccessfulScenario();
    when(obContext.getCurrentClient()).thenReturn(client);
    when(obContext.getCurrentOrganization()).thenReturn(organization);

    registerWindow.get(parameters, responseVars);

    verify(window).setClient(client);
    verify(window).setOrganization(organization);
    verify(menu).setClient(client);
    verify(menu).setOrganization(organization);
  }

  /**
   * Confirms that large Description and HelpComment inputs are accepted
   * and processed without errors.
   */
  @Test
  void testGetWithLongDescriptionShouldProcess() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);
    parameters.put(DESCRIPTION, "A".repeat(500));
    parameters.put(HELP_COMMENT, "B".repeat(500));

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures that when multiple DataPackages exist for a module,
   * the service uses the first one in the list for Window creation.
   */
  @Test
  void testGetShouldUseFirstDataPackageFromList() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);

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
    when(window.getName()).thenReturn(TEST_WINDOW);
    when(window.getId()).thenReturn(WINDOW_123);
    when(window.getModule()).thenReturn(module);

    registerWindow.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    verify(module).getDataPackageList();
  }

  /**
   * Verifies that the input parameters map remains unchanged after execution,
   * ensuring the method does not mutate caller-provided data.
   */
  @Test
  void testGetShouldNotModifyInputParameters() {
    parameters.put(DB_PREFIX, "TEST");
    parameters.put("Name", TEST_WINDOW);
    Map<String, String> originalParams = new HashMap<>(parameters);

    setupSuccessfulScenario();

    registerWindow.get(parameters, responseVars);

    assertEquals(originalParams, parameters);
  }

  /**
   * Prepares all required mocks for a successful RegisterWindow execution.
   *
   * <p>This includes:
   * <ul>
   *   <li>Resolving the DB prefix into a ModuleDBPrefix</li>
   *   <li>Validating the module is in development mode</li>
   *   <li>Providing at least one DataPackage associated with the module</li>
   *   <li>Mocking Window and Menu instantiation via OBProvider</li>
   *   <li>Mocking common getters such as window name, ID and module</li>
   * </ul>
   *
   * <p>This helper removes repeated setup logic across multiple tests.
   */
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
    when(window.getName()).thenReturn(TEST_WINDOW);
    when(window.getId()).thenReturn(WINDOW_123);
    when(window.getModule()).thenReturn(module);
  }
}
