package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.TestConstants.DB_PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.TAB_CREATED;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.TAB_CREATED_MSG;
import static com.etendoerp.copilot.devassistant.TestConstants.TAB_LEVEL;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_TABLE;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_TABLE_CAMEL;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_WINDOW;
import static com.etendoerp.copilot.devassistant.TestConstants.WINDOW_123;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link RegisterTab}.
 *
 * This test suite validates the behavior of the get(Map, Map) method in the
 * RegisterTab service, covering success scenarios, tab name formatting,
 * data package handling, and error control.
 *
 * Static mocks are used for OBDal, OBProvider, OBContext, Utils and
 * OBMessageUtils to isolate the logic from the DAL and utility layers.
 *
 * Test coverage:
 * - Detect existing tabs and return an error message.
 * - Name formatting (initial capitalization and replacing underscores with spaces).
 * - Append " Header" suffix when TabLevel is 0.
 * - Conditionally associate Window to Table depending on DataPackage.
 * - Parse SequenceNumber and TabLevel.
 * - Save order (save Window before creating Tab), flush and rollback.
 */
@ExtendWith(MockitoExtension.class)
class RegisterTabTest {

  @InjectMocks
  private RegisterTab service;

  @Mock
  private OBDal obDal;

  @Mock
  private OBProvider obProvider;

  @Mock
  private OBContext obContext;

  @Mock
  private Window window;

  @Mock
  private Table table;

  @Mock
  private Tab tab;

  @Mock
  private Tab existingTab;

  @Mock
  private Module module;

  @Mock
  private DataPackage dataPackage;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<Utils> utilsMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> requestParams;
  private Map<String, String> responseVars;
  private List<Tab> tabList;

  /**
   * Initializes static mocks and collections used by each test.
   *
   * Initialized mocks:
   * - OBDal.getInstance()
   * - OBProvider.getInstance()
   * - OBContext.getOBContext()
   * - Utils (static utility methods)
   * - OBMessageUtils (translatable messages)
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
    tabList = new ArrayList<>();
  }

  /**
   * Releases the static mocks opened during setUp() to avoid state leaks
   * across tests.
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
   * Verifies that when a tab already exists for the same table in the window,
   * the service returns an error message and does not attempt to persist a new Tab.
   */
  @Test
  void testGetWhenTabAlreadyExistsShouldReturnError() {
    setupValidRequestParams();
    setupMocksWithExistingTab();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_TabAlreadyExists"))
        .thenReturn("Tab '%s' (ID: %s) already exists in window '%s'");

    when(existingTab.getName()).thenReturn("Existing Tab");
    when(existingTab.getId()).thenReturn("existingTab123");
    when(existingTab.getWindow()).thenReturn(window);
    when(window.getName()).thenReturn(TEST_WINDOW);

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Existing Tab"));
    assertTrue(responseVars.get(ERROR).contains("existingTab123"));
    verify(obDal, never()).save(any(Tab.class));
  }

  /**
   * Ensures the created tab name starts with an uppercase letter when the
   * table name comes in lowercase from the DB.
   */
  @Test
  void testGetShouldCapitalizeFirstLetterOfTabName() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    when(table.getName()).thenReturn(TEST_TABLE);

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    ArgumentCaptor<Tab> tabCaptor = ArgumentCaptor.forClass(Tab.class);

    service.get(requestParams, responseVars);

    verify(obDal, atLeastOnce()).save(tabCaptor.capture());

    verify(tab).setName(argThat(name -> name != null && Character.isUpperCase(name.charAt(0))));
  }

  /**
   * Verifies that underscores in the table name are replaced with spaces in the
   * final tab name.
   */
  @Test
  void testGetShouldReplaceUnderscoresWithSpaces() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    when(table.getName()).thenReturn("Test_Table_Name");

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(tab).setName(argThat(name -> name != null && !name.contains("_")));
  }

  /**
   * When TabLevel is 0, the tab name must end with the " Header" suffix.
   */
  @Test
  void testGetWithTabLevel0ShouldAppendHeader() {
    setupValidRequestParams();
    requestParams.put(TAB_LEVEL, "0");
    setupMocksForSuccessfulCreation();

    when(table.getName()).thenReturn(TEST_TABLE_CAMEL);

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(tab).setName(argThat(name -> name != null && name.endsWith(" Header")));
  }

  /**
   * When TabLevel is greater than 0, the " Header" suffix must not be added to the name.
   */
  @Test
  void testGetWithTabLevelGreaterThan0ShouldNotAppendHeader() {
    setupValidRequestParams();
    requestParams.put(TAB_LEVEL, "1");
    setupMocksForSuccessfulCreation();

    when(table.getName()).thenReturn(TEST_TABLE_CAMEL);

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(tab).setName(argThat(name -> name != null && !name.endsWith(" Header")));
  }

  /**
   * If the table's DataPackage matches the module detected by the prefix, the
   * Window must be associated to the Table and the change persisted.
   */
  @Test
  void testGetWhenTableDataPackageMatchesModuleShouldSetWindowOnTable() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    when(table.getDataPackage()).thenReturn(dataPackage);
    when(dataPackage.getId()).thenReturn("package123");

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(table).setWindow(window);
    verify(obDal, atLeast(1)).save(table);
  }

  /**
   * If the table's DataPackage does not match the target module, the window
   * must not be set on the table.
   */
  @Test
  void testGetWhenTableDataPackageDoesNotMatchShouldNotSetWindowOnTable() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    DataPackage differentPackage = mock(DataPackage.class);
    when(table.getDataPackage()).thenReturn(differentPackage);
    when(dataPackage.getId()).thenReturn("package123");
    when(differentPackage.getId()).thenReturn("differentPackage456");

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(table, never()).setWindow(any());
  }

  /**
   * If the table has no DataPackage, the window must not be set on the table.
   */
  @Test
  void testGetWhenTableDataPackageIsNullShouldNotSetWindowOnTable() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    when(table.getDataPackage()).thenReturn(null);

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(table, never()).setWindow(any());
  }

  /**
   * When an exception occurs during execution, the service must rollback and
   * return an error message with the cause details.
   */
  @Test
  void testGetWhenExceptionOccursShouldRollbackAndReturnError() {
    setupValidRequestParams();

    utilsMock.when(() -> Utils.getTableByDBName(TEST_TABLE))
        .thenThrow(new RuntimeException("Database error"));

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Database error", responseVars.get(ERROR));
    verify(obDal).rollbackAndClose();
  }

  /**
   * Verifies that the Window is saved before (or during) the Tab creation,
   * ensuring header changes are persisted.
   */
  @Test
  void testGetShouldSaveWindowBeforeCreatingTab() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(obDal, atLeast(1)).save(window);
  }

  /**
   * Ensures that the SequenceNumber parameter is correctly parsed to Long and
   * set on the created tab.
   */
  @Test
  void testGetWithSequenceNumberShouldParseCorrectly() {
    setupValidRequestParams();
    requestParams.put("SequenceNumber", "100");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(tab).setSequenceNumber(100L);
  }

  /**
   * Ensures that the TabLevel parameter is correctly parsed to Long and set
   * on the created tab.
   */
  @Test
  void testGetWithTabLevel2ShouldSetCorrectly() {
    setupValidRequestParams();
    requestParams.put(TAB_LEVEL, "2");
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(tab).setTabLevel(2L);
  }

  /**
   * Verifies that flush() is called after persisting changes in the database.
   */
  @Test
  void testGetShouldCallFlushAfterSaving() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn(TAB_CREATED_MSG);

    service.get(requestParams, responseVars);

    verify(obDal).flush();
  }

  /**
   * Validates that the success message contains relevant details of the created tab,
   * including name, id, level, table, and window.
   */
  @Test
  void testGetSuccessMessageShouldContainTabDetails() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    when(tab.getName()).thenReturn("Test Table Header");
    when(tab.getId()).thenReturn("tab123");
    when(tab.getTabLevel()).thenReturn(1L);
    when(tab.getTable()).thenReturn(table);
    when(table.getName()).thenReturn(TEST_TABLE_CAMEL);
    when(window.getName()).thenReturn(TEST_WINDOW);

    messageMock.when(() -> OBMessageUtils.messageBD(TAB_CREATED))
        .thenReturn("Tab '%s' (ID: %s) created at level %s for table '%s' in window '%s'");

    service.get(requestParams, responseVars);

    String message = responseVars.get("message");
    assertNotNull(message);
    assertTrue(message.contains("Test Table Header"));
    assertTrue(message.contains("tab123"));
  }

  /**
   * Configures valid request parameters for the simulated tests.
   *
   * Parameters set:
   * - WindowID
   * - TabLevel
   * - Description
   * - HelpComment
   * - TableName
   * - SequenceNumber
   * - DBPrefix
   */
  private void setupValidRequestParams() {
    requestParams.put("WindowID", WINDOW_123);
    requestParams.put(TAB_LEVEL, "1");
    requestParams.put(DESCRIPTION, "Test description");
    requestParams.put("HelpComment", "Test help comment");
    requestParams.put("TableName", TEST_TABLE);
    requestParams.put("SequenceNumber", "10");
    requestParams.put(DB_PREFIX, "TEST");
  }

  /**
   * Prepares the mocks required for the successful creation flow of a Tab.
   * This includes obtaining the Table, Module, DataPackage, and providing a
   * Tab instance via OBProvider.
   */
  private void setupMocksForSuccessfulCreation() {
    utilsMock.when(() -> Utils.getTableByDBName(TEST_TABLE)).thenReturn(table);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
    utilsMock.when(() -> Utils.getDataPackage(module)).thenReturn(dataPackage);

    when(obDal.get(Window.class, WINDOW_123)).thenReturn(window);
    when(table.getName()).thenReturn(TEST_TABLE_CAMEL);

    when(window.getADTabList()).thenReturn(tabList);
    when(obProvider.get(Tab.class)).thenReturn(tab);
  }

  /**
   * Prepares mocks to simulate the previous existence of a tab associated with
   * the same table in the target window.
   */
  private void setupMocksWithExistingTab() {
    utilsMock.when(() -> Utils.getTableByDBName(TEST_TABLE)).thenReturn(table);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
    utilsMock.when(() -> Utils.getDataPackage(module)).thenReturn(dataPackage);

    when(obDal.get(Window.class, WINDOW_123)).thenReturn(window);
    when(table.getId()).thenReturn("table123");
    when(table.getName()).thenReturn(TEST_TABLE_CAMEL);

    tabList.add(existingTab);
    when(window.getADTabList()).thenReturn(tabList);
    when(existingTab.getTable()).thenReturn(table);
  }
}
