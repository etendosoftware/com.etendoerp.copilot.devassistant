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
package com.etendoerp.copilot.devassistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.Restriction;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;

/**
 * Unit tests for {@link TableRegistrationUtils}.
 */
@ExtendWith(MockitoExtension.class)
class TableRegistrationUtilsTest {

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria<Table> tableCriteria;

  @Mock
  private OBCriteria<ModuleDBPrefix> prefixCriteria;

  @Mock
  private Table table;

  @Mock
  private Module module;

  @Mock
  private ModuleDBPrefix moduleDBPrefix;

  @Mock
  private OBError obError;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBMessageUtils> messageMock;
  private MockedStatic<Utils> utilsMock;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    messageMock = mockStatic(OBMessageUtils.class);
    utilsMock = mockStatic(Utils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_TableNameAlreadyUse"))
        .thenReturn("Table name already used");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ModulePrefixNotFound"))
        .thenReturn("Module prefix not found for %s");
  }

  @AfterEach
  void tearDown() {
    obDalMock.close();
    messageMock.close();
    utilsMock.close();
  }

  @Test
  void testPrivateConstructorShouldThrowAssertionError() throws Exception {
    Constructor<TableRegistrationUtils> constructor = TableRegistrationUtils.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    InvocationTargetException exception = assertThrows(InvocationTargetException.class,
        constructor::newInstance);

    assertTrue(exception.getCause() instanceof AssertionError);
    assertEquals("Utility class - do not instantiate", exception.getCause().getMessage());
  }

  @Test
  void testDetermineJavaClassNameShouldBuildCamelCaseWhenJavaClassIsEmpty() {
    String result = TableRegistrationUtils.determineJavaClassName("test_table_name", "");

    assertEquals("TestTableName", result);
  }

  @Test
  void testDetermineJavaClassNameShouldBuildCamelCaseWhenJavaClassIsLiteralNull() {
    String result = TableRegistrationUtils.determineJavaClassName("test_table", "null");

    assertEquals("TestTable", result);
  }

  @Test
  void testDetermineJavaClassNameShouldReturnProvidedJavaClass() {
    String result = TableRegistrationUtils.determineJavaClassName("ignored_name", "com.test.MyClass");

    assertEquals("com.test.MyClass", result);
  }

  @Test
  void testAlreadyExistTableShouldReturnTrueWhenTableDoesNotExist() {
    when(obDal.createCriteria(Table.class)).thenReturn(tableCriteria);
    when(tableCriteria.add(any(Restriction.class))).thenReturn(tableCriteria);
    when(tableCriteria.setMaxResults(1)).thenReturn(tableCriteria);
    when(tableCriteria.uniqueResult()).thenReturn(null);

    assertDoesNotThrow(() -> assertTrue(TableRegistrationUtils.alreadyExistTable("test_table")));
  }

  @Test
  void testAlreadyExistTableShouldThrowWhenTableExists() {
    when(obDal.createCriteria(Table.class)).thenReturn(tableCriteria);
    when(tableCriteria.add(any(Restriction.class))).thenReturn(tableCriteria);
    when(tableCriteria.setMaxResults(1)).thenReturn(tableCriteria);
    when(tableCriteria.uniqueResult()).thenReturn(table);

    OBException exception = assertThrows(OBException.class,
        () -> TableRegistrationUtils.alreadyExistTable("test_table"));

    assertEquals("Table name already used", exception.getMessage());
  }

  @Test
  void testGetModuleAndPrefixShouldReturnModuleAndLowercasePrefix() {
    utilsMock.when(() -> Utils.getModuleByID("module-1")).thenReturn(module);
    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCriteria);
    when(prefixCriteria.add(any(Restriction.class))).thenReturn(prefixCriteria);
    when(prefixCriteria.list()).thenReturn(List.of(moduleDBPrefix));
    when(moduleDBPrefix.getName()).thenReturn("TEST");

    Object[] result = TableRegistrationUtils.getModuleAndPrefix("module-1");

    assertSame(module, result[0]);
    assertEquals("test", result[1]);
  }

  @Test
  void testGetModuleAndPrefixShouldThrowWhenPrefixListIsEmpty() {
    utilsMock.when(() -> Utils.getModuleByID("module-1")).thenReturn(module);
    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCriteria);
    when(prefixCriteria.add(any(Restriction.class))).thenReturn(prefixCriteria);
    when(prefixCriteria.list()).thenReturn(List.of());

    OBException exception = assertThrows(OBException.class,
        () -> TableRegistrationUtils.getModuleAndPrefix("module-1"));

    assertEquals("Module prefix not found for module-1", exception.getMessage());
  }

  @Test
  void testExecuteRegisterColumnsShouldFormatProcessResponse() throws ServletException {
    utilsMock.when(() -> Utils.execPInstanceProcess(TableRegistrationUtils.REGISTER_COLUMNS_PROCESS, "record-1"))
        .thenReturn(obError);
    when(obError.getTitle()).thenReturn("Success");
    when(obError.getMessage()).thenReturn("Columns registered");

    String result = TableRegistrationUtils.executeRegisterColumns("record-1");

    assertEquals("Success - Columns registered", result);
  }
}
