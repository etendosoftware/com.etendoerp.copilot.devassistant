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

import static com.etendoerp.copilot.devassistant.TestConstants.COLUMN;
import static com.etendoerp.copilot.devassistant.TestConstants.COLUMN_123;
import static com.etendoerp.copilot.devassistant.TestConstants.COLUMN_ID;
import static com.etendoerp.copilot.devassistant.TestConstants.DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.ELEMENT;
import static com.etendoerp.copilot.devassistant.TestConstants.HELP_COMMENT;
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.NEW_DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.NEW_HELP_COMMENT;
import static com.etendoerp.copilot.devassistant.TestConstants.ORIGINAL_ELEMENT_NAME;
import static com.etendoerp.copilot.devassistant.TestConstants.PRINT;
import static com.etendoerp.copilot.devassistant.TestConstants.PRINT_TEXT;
import static com.etendoerp.copilot.devassistant.TestConstants.READ_ELEMENTS;
import static com.etendoerp.copilot.devassistant.TestConstants.RESPONSE;
import static com.etendoerp.copilot.devassistant.TestConstants.TABLE_123;
import static com.etendoerp.copilot.devassistant.TestConstants.TABLE_ID;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_COLUMN;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_COLUMN_UPPER;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.VALID_DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.WRITE_ELEMENTS;
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
    setMode("INVALID_MODE");
    assertThrows(OBException.class, () -> elementsHandler.get(parameters, responseVars));
  }

  /**
   * Verifies that omitting the Mode parameter triggers an OBException.
   */
  @Test
  void testGetWithNullModeShouldThrowException() {
    assertThrows(OBException.class, () -> elementsHandler.get(parameters, responseVars));
  }

  /**
   * Verifies that providing an empty Mode parameter triggers an OBException.
   */
  @Test
  void testGetWithEmptyModeShouldThrowException() {
    setMode("");
    assertThrows(OBException.class, () -> elementsHandler.get(parameters, responseVars));
  }

  /**
   * Ensures READ_ELEMENTS mode returns a payload listing columns with missing description or
   * help comment when the table contains at least one incomplete column.
   */
  @Test
  void testReadModeWithValidTableShouldReturnColumnsWithMissingData() {
    columnList.add(column1);
    columnList.add(column2);

    setupReadMode(TABLE_123, columnList);
    mockColumn(column1, "Column1", "col1", null, null);
    mockColumn(column2, "Column2", "col2", VALID_DESCRIPTION, null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(RESPONSE));
    assertFalse(responseVars.containsKey(ERROR));
    String response = responseVars.get(RESPONSE);
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
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures the response explicitly mentions that only the description is missing for a given
   * column.
   */
  @Test
  void testReadModeWithColumnMissingOnlyDescriptionShouldReportIt() {
    columnList.add(column1);
    setupReadMode(TABLE_123, columnList);

    mockColumn(column1, TEST_COLUMN, "col1", "", "Valid help");

    elementsHandler.get(parameters, responseVars);

    String response = responseVars.get(RESPONSE);
    assertTrue(response.contains(TEST_COLUMN));
    assertTrue(response.contains("missing a description"));
  }

  /**
   * Ensures the response explicitly mentions that only the help comment is missing for a given
   * column.
   */
  @Test
  void testReadModeWithColumnMissingOnlyHelpCommentShouldReportIt() {
    columnList.add(column1);
    setupReadMode(TABLE_123, columnList);

    mockColumn(column1, TEST_COLUMN, "col1", VALID_DESCRIPTION, "");

    elementsHandler.get(parameters, responseVars);

    String response = responseVars.get(RESPONSE);
    assertTrue(response.contains(TEST_COLUMN));
    assertTrue(response.contains("missing a help comment"));
  }

  /**
   * Ensures READ_ELEMENTS mode returns an empty JSON array when a table has no columns.
   */
  @Test
  void testReadModeWithEmptyColumnListShouldReturnEmptyArray() {
    setupReadMode(TABLE_123, new ArrayList<>());

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(RESPONSE));
    assertEquals("[]", responseVars.get(RESPONSE));
  }

  /**
   * Ensures WRITE_ELEMENTS mode updates the column and associated element when all parameters
   * are provided and that changes are flushed.
   */
  @Test
  void testWriteModeWithValidParametersShouldUpdateColumnAndElement() {
    setupWriteMode(COLUMN_123);
    parameters.put("Name", "New Name");
    parameters.put(DESCRIPTION, NEW_DESCRIPTION);
    parameters.put(HELP_COMMENT, NEW_HELP_COMMENT);

    when(column1.getName()).thenReturn(TEST_COLUMN_UPPER);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn(ORIGINAL_ELEMENT_NAME);
    when(element.getPrintText()).thenReturn("Original Print Text");

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));

    verify(column1).setName("TEST COLUMN");
    verify(column1).setDescription(NEW_DESCRIPTION);
    verify(column1).setHelpComment(NEW_HELP_COMMENT);
    verify(element).setName("New Name");
    verify(element).setDescription(NEW_DESCRIPTION);
    verify(element).setHelpComment(NEW_HELP_COMMENT);
    verify(obDal).flush();
  }

  /**
   * Ensures WRITE_ELEMENTS mode returns an error when the ColumnID is null.
   */
  @Test
  void testWriteModeWithNullColumnIdShouldReturnError() {
    setMode(WRITE_ELEMENTS);
    parameters.put(COLUMN_ID, null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Invalid element ID"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode returns an error when the provided ColumnID does not resolve
   * to a column.
   */
  @Test
  void testWriteModeWithInvalidColumnIdShouldReturnError() {
    setMode(WRITE_ELEMENTS);
    parameters.put(COLUMN_ID, "invalidCol");
    when(obDal.get(Column.class, "invalidCol")).thenReturn(null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("not found"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode returns an error when the resolved column has no related
   * application element.
   */
  @Test
  void testWriteModeWithColumnWithoutElementShouldReturnError() {
    setupWriteMode(COLUMN_123);
    parameters.put(DESCRIPTION, TEST_DESCRIPTION);

    when(column1.getName()).thenReturn(TEST_COLUMN_UPPER);
    when(column1.getApplicationElement()).thenReturn(null);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Element not found"));
  }

  /**
   * Ensures WRITE_ELEMENTS mode does not alter column name formatting (underscores) when the
   * column name starts with the EM_ customization prefix.
   */
  @Test
  void testWriteModeWithEMPrefixColumnShouldNotReplaceUnderscores() {
    setupWriteMode(COLUMN_123);
    parameters.put(DESCRIPTION, TEST_DESCRIPTION);
    parameters.put(HELP_COMMENT, "Test Help");

    when(column1.getName()).thenReturn("EM_TEST_COLUMN");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Element Name");
    when(element.getPrintText()).thenReturn(PRINT_TEXT);

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
    setupWriteMode(COLUMN_123);
    parameters.put("Name", null);
    parameters.put(DESCRIPTION, TEST_DESCRIPTION);

    when(column1.getName()).thenReturn(TEST_COLUMN_UPPER);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Original_Element_Name");
    when(element.getPrintText()).thenReturn(PRINT_TEXT);

    elementsHandler.get(parameters, responseVars);

    verify(element).setName(ORIGINAL_ELEMENT_NAME);
  }

  /**
   * Ensures that when Name is blank, the element name falls back to the current element name
   * formatted for display.
   */
  @Test
  void testWriteModeWithBlankNameShouldUseElementName() {
    setupWriteMode(COLUMN_123);
    parameters.put("Name", "");
    parameters.put(DESCRIPTION, TEST_DESCRIPTION);

    when(column1.getName()).thenReturn(TEST_COLUMN_UPPER);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("Original_Element_Name");
    when(element.getPrintText()).thenReturn(PRINT_TEXT);

    elementsHandler.get(parameters, responseVars);

    verify(element).setName(ORIGINAL_ELEMENT_NAME);
  }

  /**
   * Ensures that when the element name is blank, the element name is derived from the column
   * name (underscores replaced with spaces, and upper-cased words preserved as-is).
   */
  @Test
  void testWriteModeWithBlankElementNameShouldUseColumnName() {
    setupWriteMode(COLUMN_123);
    parameters.put(DESCRIPTION, TEST_DESCRIPTION);

    when(column1.getName()).thenReturn("COLUMN_NAME");
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn("");
    when(element.getPrintText()).thenReturn(PRINT_TEXT);

    elementsHandler.get(parameters, responseVars);

    verify(element).setName("COLUMN NAME");
  }

  /**
   * Ensures that when the element print text is blank, it falls back to the formatted column
   * name.
   */
  @Test
  void testWriteModeWithBlankPrintTextShouldUseColumnName() {
    setupWriteMode(COLUMN_123);
    parameters.put(DESCRIPTION, TEST_DESCRIPTION);

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
    setupWriteMode(COLUMN_123);
    parameters.put("Name", "New_Element_Name");
    parameters.put(DESCRIPTION, DESCRIPTION);

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
    setupWriteMode(COLUMN_123);

    when(column1.getName()).thenReturn(COLUMN);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn(ELEMENT);
    when(element.getPrintText()).thenReturn(PRINT);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    verify(obDal).flush();
  }

  /**
   * Ensures that a null Description parameter sets both column and element descriptions to
   * null values.
   */
  @Test
  void testWriteModeWithNullDescriptionShouldSetNull() {
    setupWriteMode(COLUMN_123);
    parameters.put(DESCRIPTION, null);
    parameters.put(HELP_COMMENT, "Help");

    when(column1.getName()).thenReturn(COLUMN);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn(ELEMENT);
    when(element.getPrintText()).thenReturn(PRINT);

    elementsHandler.get(parameters, responseVars);

    verify(column1).setDescription(null);
    verify(element).setDescription(null);
  }

  /**
   * Ensures special characters in Name and Description are handled without errors.
   */
  @Test
  void testWriteModeWithSpecialCharactersShouldProcess() {
    setupWriteMode(COLUMN_123);
    parameters.put("Name", "Name with <special> & characters");
    parameters.put(DESCRIPTION, "Description with symbols: @#$%");

    when(column1.getName()).thenReturn(COLUMN);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn(ELEMENT);
    when(element.getPrintText()).thenReturn(PRINT);

    elementsHandler.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures that changes are flushed to the persistence context after WRITE_ELEMENTS
   * processing.
   */
  @Test
  void testWriteModeShouldCallFlushToPersistChanges() {
    setupWriteMode(COLUMN_123);
    parameters.put(DESCRIPTION, "Test");

    when(column1.getName()).thenReturn(COLUMN);
    when(column1.getApplicationElement()).thenReturn(element);
    when(element.getName()).thenReturn(ELEMENT);
    when(element.getPrintText()).thenReturn(PRINT);

    elementsHandler.get(parameters, responseVars);

    verify(obDal).flush();
  }

  /**
   * Ensures READ_ELEMENTS mode response includes all columns that are missing either
   * description or help comment when multiple columns are incomplete.
   */
  @Test
  void testReadModeWithMultipleColumnsMissingDataShouldListAll() {
    columnList.add(column1);
    columnList.add(column2);
    columnList.add(column3);

    setupReadMode(TABLE_123, columnList);

    mockColumn(column1, "Col1", "col1", null, "Help");
    mockColumn(column2, "Col2", "col2", "Desc", null);
    mockColumn(column3, "Col3", "col3", null, null);

    elementsHandler.get(parameters, responseVars);

    String response = responseVars.get(RESPONSE);
    assertTrue(response.contains("Col1"));
    assertTrue(response.contains("Col2"));
    assertTrue(response.contains("Col3"));
  }

  /**
   * Verifies that the handler does not mutate the provided input parameters map.
   */
  @Test
  void testGetShouldNotModifyInputParameters() {
    setMode(READ_ELEMENTS);
    parameters.put(TABLE_ID, TABLE_123);
    Map<String, String> originalParams = new HashMap<>(parameters);

    setupReadMode(TABLE_123, new ArrayList<>());

    elementsHandler.get(parameters, responseVars);

    assertEquals(originalParams, parameters);
  }

  /**
   * Sets the Mode parameter in the request parameter map.
   *
   * @param mode the mode value to assign (e.g., READ_ELEMENTS or WRITE_ELEMENTS)
   */
  private void setMode(String mode) {
    parameters.put("Mode", mode);
  }

  /**
   * Performs the common setup required for READ_ELEMENTS tests.
   * <p>
   * This method injects the TableID parameter, configures the mocked OBDal
   * to return the provided table, and wires its associated column list.
   *
   * @param tableId the ID of the table to be used in the test
   * @param cols    the list of mocked columns that belong to the table
   */
  private void setupReadMode(String tableId, List<Column> cols) {
    setMode(READ_ELEMENTS);
    parameters.put("TableID", tableId);
    when(obDal.get(Table.class, tableId)).thenReturn(table);
    when(table.getADColumnList()).thenReturn(cols);
  }

  /**
   * Performs the common setup required for WRITE_ELEMENTS tests.
   * <p>
   * This method sets the mode and ColumnID parameter, and configures the mocked
   * OBDal to return the corresponding mocked Column instance.
   *
   * @param columnId the ID of the column to be used in the test
   */
  private void setupWriteMode(String columnId) {
    setMode(WRITE_ELEMENTS);
    parameters.put(COLUMN_ID, columnId);
    when(obDal.get(Column.class, columnId)).thenReturn(column1);
  }

  /**
   * Mocks a Column instance with the provided metadata.
   * <p>
   * This helper reduces boilerplate in tests by setting the column's name, ID,
   * description, and help comment in a single call.
   *
   * @param col  the mocked Column instance
   * @param name the column's name
   * @param id   the column's unique identifier
   * @param desc the description value to mock (may be null)
   * @param help the help comment value to mock (may be null)
   */
  private void mockColumn(Column col, String name, String id, String desc, String help) {
    when(col.getName()).thenReturn(name);
    when(col.getId()).thenReturn(id);
    when(col.getDescription()).thenReturn(desc);
    when(col.getHelpComment()).thenReturn(help);
  }
}
