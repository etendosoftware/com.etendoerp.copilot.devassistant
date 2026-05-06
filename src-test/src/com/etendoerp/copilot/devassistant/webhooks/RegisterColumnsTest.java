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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
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
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.Restriction;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.copilot.devassistant.TableRegistrationUtils;
import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link RegisterColumns}.
 */
@ExtendWith(MockitoExtension.class)
class RegisterColumnsTest {

  private static final String TABLE_NAME_PARAM = "TableName";
  private static final String TABLE_ID = "table-1";

  @InjectMocks
  private RegisterColumns registerColumns;

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria<Table> tableCriteria;

  @Mock
  private Table table;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBMessageUtils> messageMock;
  private MockedStatic<TableRegistrationUtils> tableRegUtilsMock;
  private MockedStatic<Utils> utilsMock;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    messageMock = mockStatic(OBMessageUtils.class);
    tableRegUtilsMock = mockStatic(TableRegistrationUtils.class);
    utilsMock = mockStatic(Utils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_TableNotFound"))
        .thenReturn("Table not found: %s");
  }

  @AfterEach
  void tearDown() {
    obDalMock.close();
    messageMock.close();
    tableRegUtilsMock.close();
    utilsMock.close();
  }

  @Test
  void testGetShouldReturnMessageWhenColumnsAreRegistered() {
    Map<String, String> params = new HashMap<>();
    Map<String, String> responseVars = new HashMap<>();
    params.put(TABLE_NAME_PARAM, "C_Order");

    when(obDal.createCriteria(Table.class)).thenReturn(tableCriteria);
    when(tableCriteria.add(any(Restriction.class))).thenReturn(tableCriteria);
    when(tableCriteria.setMaxResults(1)).thenReturn(tableCriteria);
    when(tableCriteria.uniqueResult()).thenReturn(table);
    when(table.getId()).thenReturn(TABLE_ID);
    tableRegUtilsMock.when(() -> TableRegistrationUtils.executeRegisterColumns(TABLE_ID))
        .thenReturn("Success - Columns registered");

    registerColumns.get(params, responseVars);

    assertEquals("Success - Columns registered", responseVars.get("message"));
  }

  @Test
  void testGetShouldReturnFormattedMessageWhenTableDoesNotExist() {
    Map<String, String> params = new HashMap<>();
    Map<String, String> responseVars = new HashMap<>();
    params.put(TABLE_NAME_PARAM, "UNKNOWN_TABLE");

    when(obDal.createCriteria(Table.class)).thenReturn(tableCriteria);
    when(tableCriteria.add(any(Restriction.class))).thenReturn(tableCriteria);
    when(tableCriteria.setMaxResults(1)).thenReturn(tableCriteria);
    when(tableCriteria.uniqueResult()).thenReturn(null);

    registerColumns.get(params, responseVars);

    assertEquals("Table not found: UNKNOWN_TABLE", responseVars.get("message"));
  }

  @Test
  void testGetShouldStoreErrorWhenExecutionFails() {
    Map<String, String> params = new HashMap<>();
    Map<String, String> responseVars = new HashMap<>();
    params.put(TABLE_NAME_PARAM, "C_Order");

    when(obDal.createCriteria(Table.class)).thenReturn(tableCriteria);
    when(tableCriteria.add(any(Restriction.class))).thenReturn(tableCriteria);
    when(tableCriteria.setMaxResults(1)).thenReturn(tableCriteria);
    when(tableCriteria.uniqueResult()).thenReturn(table);
    when(table.getId()).thenReturn(TABLE_ID);
    tableRegUtilsMock.when(() -> TableRegistrationUtils.executeRegisterColumns(TABLE_ID))
        .thenThrow(new RuntimeException("Process failed"));

    registerColumns.get(params, responseVars);

    assertTrue(responseVars.containsKey(RegisterColumns.ERROR_PROPERTY));
    assertEquals("Process failed", responseVars.get(RegisterColumns.ERROR_PROPERTY));
  }
}
