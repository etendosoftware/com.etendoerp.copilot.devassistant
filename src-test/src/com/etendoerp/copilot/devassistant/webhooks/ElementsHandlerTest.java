package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Element;

/**
 * Unit tests for {@link ElementsHandler}.
 * <p>
 * This suite verifies the behavior of the ElementsHandler webhook entry point for two
 * operational modes:
 * <ul>
 *   <li>READ_ELEMENTS: inspects a table's columns and reports which ones are missing
 *   description and/or help comment.</li>
 *   <li>WRITE_ELEMENTS: updates column and linked element metadata based on provided
 *   parameters and persists changes.</li>
 * </ul>
 * The tests rely on Mockito to isolate dependencies (OBDal, Table, Column, Element) and on
 * OBMessageUtils for i18n messages. All external static access is controlled using
 * static-mocking.
 */
@ExtendWith(MockitoExtension.class)
class ElementsHandlerTest {

  @InjectMocks
  private ElementsHandler elementsHandler;

  @Mock
  private OBDal obDal;

  @Mock
  private Table table;

  @Mock
  private Column column1;

  @Mock
  private Column column2;

  @Mock
  private Column column3;

  @Mock
  private Element element;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;
  private List<Column> columnList;

  /**
   * Initializes static mocks, common parameters/containers and message stubs used across tests.
   * It also wires OBDal.getInstance() to the mocked OBDal.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    messageMock = mockStatic(OBMessageUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);

    // Mock messages
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_WrongMode"))
        .thenReturn("Wrong mode specified");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_InvalidElementID"))
        .thenReturn("Invalid element ID");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_Help&DescriptionAdded"))
        .thenReturn("Help and description added for column '%s', element '%s', print text '%s'");

    parameters = new HashMap<>();
    responseVars = new HashMap<>();
    columnList = new ArrayList<>();
  }

  /**
   * Closes static mocks after each test to avoid cross-test interference.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    messageMock.close();
  }

  /**
   * Verifies that providing an unknown Mode value triggers an OBException.
   */
  @Test
  void testGetWithInvalidModeShouldThrowException() {
    parameters.put("Mode", "INVALID_MODE");

    // Execute & Verify
    assertThrows(OBException.class, () -> elementsHandler.get(parameters, responseVars));
  }

  /**
   * Verifies that omitting the Mode parameter triggers an OBException.
   */
  @Test
  void testGetWithNullModeShouldThrowException() {
    // Setup - Mode not provided

    // Execute & Verify
    assertThrows(OBException.class, () -> elementsHandler.get(parameters, responseVars));
  }

  /**
   * Verifies that providing an empty Mode parameter triggers an OBException.
   */
  @Test
  void testGetWithEmptyModeShouldThrowException() {
    parameters.put("Mode", "");

    // Execute & Verify
    assertThrows(OBException.class, () -> elementsHandler.get(parameters, responseVars));
  }

  /**
   * Ensures READ_ELEMENTS mode returns a payload listing columns with missing description or
   * help comment when the table contains at least one incomplete column.
   */
  @Test
  void testReadModeWithValidTableShouldReturnColumnsWithMissingData() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");

    columnList.add(column1);
    columnList.add(column2);

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(columnList);

    when(column1.getName()).thenReturn("Column1");
    when(column1.getId()).thenReturn("col1");
    when(column1.getDescription()).thenReturn(null);
    when(column1.getHelpComment()).thenReturn(null);

    when(column2.getName()).thenReturn("Column2");
    when(column2.getId()).thenReturn("col2");
    when(column2.getDescription()).thenReturn("Valid description");
    when(column2.getHelpComment()).thenReturn(null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("response"));
    assertFalse(responseVars.containsKey("error"));

    String response = responseVars.get("response");
    assertTrue(response.contains("Column1"));
    assertTrue(response.contains("Column2"));
    assertTrue(response.contains("missing"));
  }

  /**
   * Ensures READ_ELEMENTS mode returns an empty JSON array when all columns are fully
   * documented.
   */
  @Test
  void testReadModeWithAllColumnsCompleteShouldReturnEmptyArray() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");

    columnList.add(column1);

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(columnList);

    when(column1.getDescription()).thenReturn("Valid description");
    when(column1.getHelpComment()).thenReturn("Valid help comment");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("response"));
    assertEquals("[]", responseVars.get("response"));
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Ensures the response explicitly mentions that only the description is missing for a given
   * column.
   */
  @Test
  void testReadModeWithColumnMissingOnlyDescriptionShouldReportIt() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");

    columnList.add(column1);

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(columnList);

    when(column1.getName()).thenReturn("TestColumn");
    when(column1.getId()).thenReturn("col1");
    when(column1.getDescription()).thenReturn("");
    when(column1.getHelpComment()).thenReturn("Valid help");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("response"));
    String response = responseVars.get("response");
    assertTrue(response.contains("TestColumn"));
    assertTrue(response.contains("missing a description"));
  }

  /**
   * Ensures the response explicitly mentions that only the help comment is missing for a given
   * column.
   */
  @Test
  void testReadModeWithColumnMissingOnlyHelpCommentShouldReportIt() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");

    columnList.add(column1);

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(columnList);

    when(column1.getName()).thenReturn("TestColumn");
    when(column1.getId()).thenReturn("col1");
    when(column1.getDescription()).thenReturn("Valid description");
    when(column1.getHelpComment()).thenReturn("");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("response"));
    String response = responseVars.get("response");
    assertTrue(response.contains("TestColumn"));
    assertTrue(response.contains("missing a help comment"));
  }

  /**
   * Ensures READ_ELEMENTS mode returns an empty JSON array when a table has no columns.
   */
  @Test
  void testReadModeWithEmptyColumnListShouldReturnEmptyArray() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(new ArrayList<>());

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("response"));
    assertEquals("[]", responseVars.get("response"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode updates the column and associated element when all parameters
   * are provided and that changes are flushed.
   */
  @Test
  void testWriteModeWithValidParametersShouldUpdateColumnAndElement() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Name", "New Name");
    parameters.put("Description", "New Description");
    parameters.put("HelpComment", "New Help Comment");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("TEST_COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Original Element Name");
    when(element.getPrintText()).thenReturn("Original Print Text");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));

    verify(column1).setName("TEST COLUMN");
    verify(column1).setDescription("New Description");
    verify(column1).setHelpComment("New Help Comment");
    verify(element).setName("New Name");
    verify(element).setDescription("New Description");
    verify(element).setHelpComment("New Help Comment");
    verify(obDal).flush();
  }

  /**
   * Ensures WRITE_ELEMENTS mode returns an error when the ColumnID is null.
   */
  @Test
  void testWriteModeWithNullColumnIdShouldReturnError() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("Invalid element ID"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode returns an error when the provided ColumnID does not resolve
   * to a column.
   */
  @Test
  void testWriteModeWithInvalidColumnIdShouldReturnError() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "invalidCol");

    when(obDal.get(Column.class, "invalidCol")).thenReturn(null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("not found"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode returns an error when the resolved column has no related
   * application element.
   */
  @Test
  void testWriteModeWithColumnWithoutElementShouldReturnError() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Description", "Test Description");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("TEST_COLUMN");
    when(column1.getApplicationElement()).thenReturn(null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertTrue(responseVars.get("error").contains("Element not found"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode does not alter column name formatting (underscores) when the
   * column name starts with the EM_ customization prefix.
   */
  @Test
  void testWriteModeWithEMPrefixColumnShouldNotReplaceUnderscores() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Description", "Test Description");
    parameters.put("HelpComment", "Test Help");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("EM_TEST_COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element Name");
    when(element.getPrintText()).thenReturn("Print Text");

    elementsHandler.get(parameters, responseVars);

    verify(column1, never()).setName(anyString());
    verify(obDal).flush();
  }

  /**
   * Ensures that when Name is null, the element name is derived from its current value by
   * replacing underscores with spaces.
   */
  @Test
  void testWriteModeWithNullNameShouldUseElementName() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Name", null);
    parameters.put("Description", "Test Description");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("TEST_COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Original_Element_Name");
    when(element.getPrintText()).thenReturn("Print Text");

    elementsHandler.get(parameters, responseVars);

    verify(element).setName("Original Element Name");
  }

  /**
   * Ensures that when Name is blank, the element name falls back to the current element name
   * formatted for display.
   */
  @Test
  void testWriteModeWithBlankNameShouldUseElementName() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Name", "");
    parameters.put("Description", "Test Description");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("TEST_COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Original_Element_Name");
    when(element.getPrintText()).thenReturn("Print Text");

    elementsHandler.get(parameters, responseVars);

    verify(element).setName("Original Element Name");
  }

  /**
   * Ensures that when the element name is blank, the element name is derived from the column
   * name (underscores replaced with spaces, and upper-cased words preserved as-is).
   */
  @Test
  void testWriteModeWithBlankElementNameShouldUseColumnName() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Description", "Test Description");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("COLUMN_NAME");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("");
    when(element.getPrintText()).thenReturn("Print Text");

    elementsHandler.get(parameters, responseVars);

    verify(element).setName("COLUMN NAME");
  }

  /**
   * Ensures that when the element print text is blank, it falls back to the formatted column
   * name.
   */
  @Test
  void testWriteModeWithBlankPrintTextShouldUseColumnName() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Description", "Test Description");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("COLUMN_NAME");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element Name");
    when(element.getPrintText()).thenReturn("");

    elementsHandler.get(parameters, responseVars);

    verify(element).setPrintText("COLUMN NAME");
  }

  /**
   * Verifies underscore-to-space replacement in both column and element names during
   * WRITE_ELEMENTS updates, and preserves existing non-empty print text.
   */
  @Test
  void testWriteModeShouldReplaceUnderscoresWithSpaces() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Name", "New_Element_Name");
    parameters.put("Description", "Description");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("TEST_COLUMN_NAME");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Original_Name");
    when(element.getPrintText()).thenReturn("Original_Print_Text");

    elementsHandler.get(parameters, responseVars);

    verify(column1).setName("TEST COLUMN NAME");
    verify(element).setName("New Element Name");
    verify(element).setPrintText("Original Print Text");
  }

  /**
   * Ensures WRITE_ELEMENTS mode completes successfully with only the required parameters
   * (Mode and ColumnID) and persists changes.
   */
  @Test
  void testWriteModeWithOnlyRequiredParametersShouldWork() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element");
    when(element.getPrintText()).thenReturn("Print");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    verify(obDal).flush();
  }

  /**
   * Ensures that a null Description parameter sets both column and element descriptions to
   * null values.
   */
  @Test
  void testWriteModeWithNullDescriptionShouldSetNull() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Description", null);
    parameters.put("HelpComment", "Help");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element");
    when(element.getPrintText()).thenReturn("Print");

    elementsHandler.get(parameters, responseVars);

    verify(column1).setDescription(null);
    verify(element).setDescription(null);
  }

  /**
   * Ensures special characters in Name and Description are handled without errors.
   */
  @Test
  void testWriteModeWithSpecialCharactersShouldProcess() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Name", "Name with <special> & characters");
    parameters.put("Description", "Description with symbols: @#$%");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element");
    when(element.getPrintText()).thenReturn("Print");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("message"));
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Ensures that changes are flushed to the persistence context after WRITE_ELEMENTS
   * processing.
   */
  @Test
  void testWriteModeShouldCallFlushToPersistChanges() {
    parameters.put("Mode", "WRITE_ELEMENTS");
    parameters.put("ColumnID", "col123");
    parameters.put("Description", "Test");

    when(obDal.get(Column.class, "col123")).thenReturn(column1);
    when(column1.getName()).thenReturn("COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element");
    when(element.getPrintText()).thenReturn("Print");

    elementsHandler.get(parameters, responseVars);

    verify(obDal).flush();
  }

  /**
   * Ensures READ_ELEMENTS mode response includes all columns that are missing either
   * description or help comment when multiple columns are incomplete.
   */
  @Test
  void testReadModeWithMultipleColumnsMissingDataShouldListAll() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");

    columnList.add(column1);
    columnList.add(column2);
    columnList.add(column3);

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(columnList);

    when(column1.getName()).thenReturn("Col1");
    when(column1.getId()).thenReturn("col1");
    when(column1.getDescription()).thenReturn(null);
    when(column1.getHelpComment()).thenReturn("Help");

    when(column2.getName()).thenReturn("Col2");
    when(column2.getId()).thenReturn("col2");
    when(column2.getDescription()).thenReturn("Desc");
    when(column2.getHelpComment()).thenReturn(null);

    when(column3.getName()).thenReturn("Col3");
    when(column3.getId()).thenReturn("col3");
    when(column3.getDescription()).thenReturn(null);
    when(column3.getHelpComment()).thenReturn(null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("response"));
    String response = responseVars.get("response");
    assertTrue(response.contains("Col1"));
    assertTrue(response.contains("Col2"));
    assertTrue(response.contains("Col3"));
  }

  /**
   * Verifies that the handler does not mutate the provided input parameters map.
   */
  @Test
  void testGetShouldNotModifyInputParameters() {
    parameters.put("Mode", "READ_ELEMENTS");
    parameters.put("TableID", "table123");
    Map<String, String> originalParams = new HashMap<>(parameters);

    when(obDal.get(Table.class, "table123")).thenReturn(table);
    when(table.getADColumnList()).thenReturn(new ArrayList<>());

    elementsHandler.get(parameters, responseVars);

    assertEquals(originalParams, parameters);
  }
}
