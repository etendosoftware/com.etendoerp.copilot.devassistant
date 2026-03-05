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
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.MODULE_123;
import static com.etendoerp.copilot.devassistant.TestConstants.MODULE_ID;
import static com.etendoerp.copilot.devassistant.TestConstants.TABLE_123;
import static com.etendoerp.copilot.devassistant.TestConstants.TABLE_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link CreateComputedColumn}.
 */
@ExtendWith(MockitoExtension.class)
class CreateComputedColumnTest {

  private static final String COLUMN_NAME = "ColumnName";
  private static final String SQL_LOGIC = "SQLLogic";
  private static final String TEST_SQL = "(SELECT name FROM m_product LIMIT 1)";
  private static final String TEST_COL_NAME = "computed_col";
  private static final String TEST_DISPLAY_NAME = "Computed Column";
  private static final String TEST_TABLE_NAME = TEST_TABLE_NAME;

  @InjectMocks
  private CreateComputedColumn createComputedColumn;

  @Mock private OBDal obDal;
  @Mock private OBProvider obProvider;
  @Mock private OBContext obContext;
  @Mock private Table table;
  @Mock private Module module;
  @Mock private Reference reference;
  @Mock private Column column;
  @Mock private Client client;
  @Mock private Organization organization;
  @Mock private User user;
  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<OBMessageUtils> messageMock;
  private MockedStatic<Utils> utilsMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obProviderMock = mockStatic(OBProvider.class);
    obContextMock = mockStatic(OBContext.class);
    messageMock = mockStatic(OBMessageUtils.class);
    utilsMock = mockStatic(Utils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obProviderMock.when(OBProvider::getInstance).thenReturn(obProvider);
    obContextMock.when(OBContext::getOBContext).thenReturn(obContext);
    parameters = new HashMap<>();
    responseVars = new HashMap<>();
  }

  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    messageMock.close();
    utilsMock.close();
  }

  @Test
  void testGetWithMissingColumnNameShouldReturnError() {
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("ColumnName parameter is required"));
  }

  @Test
  void testGetWithMissingNameShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Name parameter is required"));
  }

  @Test
  void testGetWithMissingSQLLogicShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("SQLLogic parameter is required"));
  }

  @Test
  void testGetWithMissingModuleIDShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(TABLE_ID, TABLE_123);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("ModuleID parameter is required"));
  }

  @Test
  void testGetWithMissingBothTableParamsShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Either TableID or TableName"));
  }

  @Test
  void testGetWithValidParametersByTableIDShouldCreateColumn() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);

    setupSuccessfulScenario();
    when(obDal.get(Table.class, TABLE_123)).thenReturn(table);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal).save(any(Column.class));
    verify(obDal).flush();
  }

  @Test
  void testGetWithValidParametersByTableNameShouldCreateColumn() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put("TableName", TEST_TABLE_NAME);

    setupSuccessfulScenario();
    utilsMock.when(() -> Utils.getTableByDBName(TEST_TABLE_NAME)).thenReturn(table);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetWithNonExistentTableIDShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, "nonexistent");

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Table.class, "nonexistent")).thenReturn(null);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("not found"));
  }

  @Test
  void testGetWithExistingColumnShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Table.class, TABLE_123)).thenReturn(table);
    when(obDal.get(Reference.class, "10")).thenReturn(reference);

    OBCriteria<Column> colCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Column.class)).thenReturn(colCrit);
    when(colCrit.add(any())).thenReturn(colCrit);
    when(colCrit.setMaxResults(anyInt())).thenReturn(colCrit);
    when(colCrit.uniqueResult()).thenReturn(column);
    when(table.getDBTableName()).thenReturn(TEST_TABLE_NAME);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("already exists"));
  }

  @Test
  void testGetWithInvalidReferenceIDShouldReturnError() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);
    parameters.put("ReferenceID", "9999");

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Table.class, TABLE_123)).thenReturn(table);
    when(obDal.get(Reference.class, "9999")).thenReturn(null);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Reference with ID"));
  }

  @Test
  void testGetWithCustomReferenceIDShouldProcess() {
    parameters.put(COLUMN_NAME, TEST_COL_NAME);
    parameters.put("Name", TEST_DISPLAY_NAME);
    parameters.put(SQL_LOGIC, TEST_SQL);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_ID, TABLE_123);
    parameters.put("ReferenceID", "22");
    parameters.put("Description", "A computed description");

    Reference customRef = mock(Reference.class);
    setupSuccessfulScenarioWithReference(customRef, "22");
    when(obDal.get(Table.class, TABLE_123)).thenReturn(table);

    createComputedColumn.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @SuppressWarnings("unchecked")
  private void setupSuccessfulScenario() {
    setupSuccessfulScenarioWithReference(reference, "10");
  }

  @SuppressWarnings("unchecked")
  private void setupSuccessfulScenarioWithReference(Reference ref, String refId) {
    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Reference.class, refId)).thenReturn(ref);

    OBCriteria<Column> colCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Column.class)).thenReturn(colCrit);
    when(colCrit.add(any())).thenReturn(colCrit);
    when(colCrit.setMaxResults(anyInt())).thenReturn(colCrit);
    when(colCrit.uniqueResult()).thenReturn(null);

    when(obDal.get(Client.class, "0")).thenReturn(client);
    when(obDal.get(Organization.class, "0")).thenReturn(organization);
    when(obProvider.get(Column.class)).thenReturn(column);
    when(obContext.getUser()).thenReturn(user);
    when(column.getId()).thenReturn("newcol123");
  }
}
