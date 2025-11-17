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

import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;

import static com.etendoerp.copilot.devassistant.TestConstants.FIELDS_REGISTERED;
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.MODULE_123;
import static com.etendoerp.copilot.devassistant.TestConstants.SUCCESS;
import static com.etendoerp.copilot.devassistant.TestConstants.TAB;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_HELP_COMMENT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Element;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link RegisterFields} webhook service.
 * <p>
 * These tests validate the behavior of the get(Map, Map) entry point which:
 * - Loads a Tab by its identifier
 * - Resolves a Module by DB prefix
 * - Iterates over the Tab field list to update help/description and grid visibility
 * - Normalizes names by replacing underscores with spaces for Fields and Elements
 * - Skips key columns from being modified
 * - Executes an underlying AD process and reports success or error in responseVars
 * - Persists and flushes changes through OBDal
 * </p>
 * Mockito is used to isolate external dependencies (OBDal, Utils, OBMessageUtils) and
 * verify side effects and interactions.
 */
@ExtendWith(MockitoExtension.class)
class RegisterFieldsTest {

  @InjectMocks
  private RegisterFields service;

  @Mock
  private OBDal obDal;

  @Mock
  private Tab tab;

  @Mock
  private Module module;

  @Mock
  private Field field1;

  @Mock
  private Field field2;

  @Mock
  private Field keyField;

  @Mock
  private Column column1;

  @Mock
  private Column column2;

  @Mock
  private Column keyColumn;

  @Mock
  private Element element1;

  @Mock
  private Element element2;

  @Mock
  private OBError obError;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<Utils> utilsMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> requestParams;
  private Map<String, String> responseVars;
  private List<Field> fieldList;

  /**
   * Initializes common test fixtures and static mocks before each test.
   * - Stubs OBDal.getInstance
   * - Prepares request/response maps and an empty field list container
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    utilsMock = mockStatic(Utils.class);
    messageMock = mockStatic(OBMessageUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);

    requestParams = new HashMap<>();
    responseVars = new HashMap<>();
    fieldList = new ArrayList<>();
  }

  /**
   * Closes the static mocks created during setup to avoid cross-test interference.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    utilsMock.close();
    messageMock.close();
  }

  /**
   * Verifies the happy-path where a valid tab and module are resolved, the AD process
   * returns Success, fields are updated and persisted, and a success message is
   * placed in responseVars.
   */
  @Test
  void testGetWithValidParametersShouldRegisterFieldsSuccessfully() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn("Fields registered successfully");

    service.get(requestParams, responseVars);

    assertEquals("Success - Fields registered successfully", responseVars.get(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal).refresh(tab);
    verify(obDal).refresh(module);
    verify(obDal, atLeastOnce()).save(any(Field.class));
    verify(obDal).flush();
  }

  /**
   * Ensures that when the Tab cannot be retrieved by id, the service returns an
   * error message using OBMessageUtils and does not attempt to persist any data.
   */
  @Test
  void testGetWithNonExistentTabShouldReturnError() {
    setupValidRequestParams();
    when(obDal.get(Tab.class, TAB)).thenReturn(null);

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_TabNotFound"))
        .thenReturn("Tab with ID %s not found");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Tab with ID tab123 not found", responseVars.get(ERROR));
    verify(obDal, never()).save(any());
  }

  /**
   * Confirms that non-key fields are updated with the provided HelpComment and
   * Description, and marked to be shown in grid view.
   */
  @Test
  void testGetShouldSetHelpCommentAndDescriptionOnFields() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(field1).setHelpComment(TEST_HELP_COMMENT);
    verify(field1).setDescription(TEST_DESCRIPTION);
    verify(field1).setShowInGridView(true);
    verify(field2).setHelpComment(TEST_HELP_COMMENT);
    verify(field2).setDescription(TEST_DESCRIPTION);
    verify(field2).setShowInGridView(true);
  }

  /**
   * Validates that key columns are explicitly ignored and receive no modifications,
   * while regular fields are still updated.
   */
  @Test
  void testGetShouldIgnoreKeyColumns() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistrationWithKeyColumn();

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(keyField, never()).setHelpComment(anyString());
    verify(keyField, never()).setDescription(anyString());
    verify(keyField, never()).setShowInGridView(anyBoolean());

    verify(field1).setHelpComment(TEST_HELP_COMMENT);
    verify(field2).setHelpComment(TEST_HELP_COMMENT);
  }

  /**
   * Verifies that underscores in field names are replaced with spaces when a name
   * value is present.
   */
  @Test
  void testGetShouldReplaceUnderscoresInFieldNames() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    when(field1.getName()).thenReturn("field_name_with_underscores");
    when(field2.getName()).thenReturn("another_field_name");

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(field1).setName("field name with underscores");
    verify(field2).setName("another field name");
  }

  /**
   * Ensures that when a field name is null no normalization is attempted, while
   * other fields continue to be normalized.
   */
  @Test
  void testGetWithNullFieldNameShouldNotReplaceUnderscores() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    when(field1.getName()).thenReturn(null);
    when(field2.getName()).thenReturn("field_name");

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(field1, never()).setName(anyString());
    verify(field2).setName("field name");
  }

  /**
   * Ensures that element name and print text are normalized and persisted only
   * when the element belongs to the same module determined by the DB prefix.
   */
  @Test
  void testGetShouldUpdateElementNamesForSameModule() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    when(element1.getModule()).thenReturn(module);
    when(element2.getModule()).thenReturn(module);
    when(module.getId()).thenReturn(MODULE_123);
    when(element1.getName()).thenReturn("element_name_one");
    when(element2.getName()).thenReturn("element_name_two");
    when(element1.getPrintText()).thenReturn("print_text_one");
    when(element2.getPrintText()).thenReturn("print_text_two");

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(element1).setName("element name one");
    verify(element1).setPrintText("print text one");
    verify(element2).setName("element name two");
    verify(element2).setPrintText("print text two");
    verify(obDal, times(2)).save(any(Element.class));
  }

  /**
   * Validates that elements belonging to a different module are not modified nor
   * persisted.
   */
  @Test
  void testGetShouldNotUpdateElementNamesForDifferentModule() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    Module differentModule = mock(Module.class);
    when(element1.getModule()).thenReturn(differentModule);
    when(element2.getModule()).thenReturn(differentModule);
    when(module.getId()).thenReturn(MODULE_123);
    when(differentModule.getId()).thenReturn("differentModule456");

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(element1, never()).setName(anyString());
    verify(element1, never()).setPrintText(anyString());
    verify(element2, never()).setName(anyString());
    verify(element2, never()).setPrintText(anyString());
    verify(obDal, never()).save(any(Element.class));
  }

  /**
   * When element name and print text are blank, verifies that the column name is
   * used as a fallback after normalization.
   */
  @Test
  void testGetWithBlankElementNameShouldUseColumnName() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    when(element1.getModule()).thenReturn(module);
    when(module.getId()).thenReturn(MODULE_123);
    when(element1.getName()).thenReturn("");
    when(element1.getPrintText()).thenReturn("");
    when(column1.getName()).thenReturn("column_name");

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> printTextCaptor = ArgumentCaptor.forClass(String.class);

    service.get(requestParams, responseVars);

    verify(element1).setName(nameCaptor.capture());
    verify(element1).setPrintText(printTextCaptor.capture());
    assertEquals("column name", nameCaptor.getValue());
    assertEquals("column name", printTextCaptor.getValue());
  }

  /**
   * Ensures that elements with a null module are skipped and not updated.
   */
  @Test
  void testGetWithNullElementModuleShouldNotUpdateElement() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    when(element1.getModule()).thenReturn(null);
    when(element2.getModule()).thenReturn(null);

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(element1, never()).setName(anyString());
    verify(element2, never()).setName(anyString());
    verify(obDal, never()).save(any(Element.class));
  }

  /**
   * Verifies that when the underlying process returns an Error, the service sets
   * an error message in responseVars and does not set a success message.
   */
  @Test
  void testGetWhenProcessFailsShouldReturnError() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn("Error");
    when(obError.getTitle()).thenReturn("Error");
    when(obError.getMessage()).thenReturn("Process execution failed");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Error - Process execution failed", responseVars.get(ERROR));
    assertFalse(responseVars.containsKey(MESSAGE));
  }

  /**
   * Confirms robustness when a tab contains no fields: the service should not
   * throw and must return the process outcome message.
   */
  @Test
  void testGetWithEmptyFieldListShouldNotThrowException() {
    setupValidRequestParams();
    when(obDal.get(Tab.class, TAB)).thenReturn(tab);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
    when(tab.getId()).thenReturn(TAB);
    when(tab.getADFieldList()).thenReturn(new ArrayList<>());

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn("No fields to register");

    assertDoesNotThrow(() -> service.get(requestParams, responseVars));
    assertEquals("Success - No fields to register", responseVars.get(MESSAGE));
  }

  /**
   * Checks that OBDal.refresh is called on both Tab and Module before performing
   * updates and persistence.
   */
  @Test
  void testGetShouldCallRefreshOnTabAndModule() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(obDal).refresh(tab);
    verify(obDal).refresh(module);
  }

  /**
   * Ensures that OBDal.flush is invoked after saving field updates to persist
   * changes to the database.
   */
  @Test
  void testGetShouldCallFlushAfterSavingFields() {
    setupValidRequestParams();
    setupMocksForSuccessfulRegistration();

    utilsMock.when(() -> Utils.execPInstanceProcess("174", TAB))
        .thenReturn(obError);

    when(obError.getType()).thenReturn(SUCCESS);
    when(obError.getTitle()).thenReturn(SUCCESS);
    when(obError.getMessage()).thenReturn(FIELDS_REGISTERED);

    service.get(requestParams, responseVars);

    verify(obDal).flush();
  }

  /**
   * Populates requestParams with a valid set of inputs used by most tests.
   */
  private void setupValidRequestParams() {
    requestParams.put("WindowTabID", TAB);
    requestParams.put("HelpComment", TEST_HELP_COMMENT);
    requestParams.put("Description", TEST_DESCRIPTION);
    requestParams.put("DBPrefix", "TEST");
  }

  /**
   * Common mocking for a successful scenario: resolves Tab and Module, prepares a
   * field list with two regular fields and wires their Column and Element data.
   */
  private void setupMocksForSuccessfulRegistration() {
    when(obDal.get(Tab.class, TAB)).thenReturn(tab);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
    when(tab.getId()).thenReturn(TAB);

    fieldList.add(field1);
    fieldList.add(field2);
    when(tab.getADFieldList()).thenReturn(fieldList);

    setupField(field1, column1, element1, false);
    setupField(field2, column2, element2, false);
  }

  /**
   * Like setupMocksForSuccessfulRegistration but adds a third field mapped to a
   * key column that should be ignored during updates.
   */
  private void setupMocksForSuccessfulRegistrationWithKeyColumn() {
    when(obDal.get(Tab.class, TAB)).thenReturn(tab);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
    when(tab.getId()).thenReturn(TAB);

    fieldList.add(field1);
    fieldList.add(field2);
    fieldList.add(keyField);
    when(tab.getADFieldList()).thenReturn(fieldList);

    setupField(field1, column1, element1, false);
    setupField(field2, column2, element2, false);
    setupField(keyField, keyColumn, null, true);
  }

  /**
   * Wires a Field to a Column and optionally an Element, configuring whether the
   * column is a key and providing default element name/printText values.
   */
  private void setupField(Field field, Column column, Element element, boolean isKeyColumn) {
    when(field.getColumn()).thenReturn(column);
    when(column.isKeyColumn()).thenReturn(isKeyColumn);

    if (!isKeyColumn && element != null) {
      when(column.getApplicationElement()).thenReturn(element);
      when(element.getName()).thenReturn("element_name");
      when(element.getPrintText()).thenReturn("print_text");
    }
  }
}
