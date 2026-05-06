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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;

/**
 * Unit tests for {@link Utils}.
 */
@ExtendWith(MockitoExtension.class)
class UtilsTest {

  @Mock
  private Logger logger;

  @Mock
  private Module module;

  @Mock
  private DataPackage dataPackage;

  private MockedStatic<OBMessageUtils> messageMock;

  @BeforeEach
  void setUp() {
    messageMock = mockStatic(OBMessageUtils.class);
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_FileType&AssistantTypeIncompatibility"))
        .thenReturn("Incompatible app type and file type");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_JavaPackageCannotBeNull"))
        .thenReturn("Java package cannot be null");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_IDCannotBeNull"))
        .thenReturn("ID cannot be null");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ModNotDev"))
        .thenReturn("Module %s is not in development");
    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ModNotDP"))
        .thenReturn("Module %s has no data package");
  }

  @AfterEach
  void tearDown() {
    messageMock.close();
  }

  @Test
  void testPrivateConstructorShouldThrowIllegalStateException() throws Exception {
    Constructor<Utils> constructor = Utils.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    InvocationTargetException exception = assertThrows(InvocationTargetException.class,
        constructor::newInstance);

    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertEquals("Utility class", exception.getCause().getMessage());
  }

  @Test
  void testLogIfDebugShouldWriteWhenLoggerIsInDebugMode() {
    when(logger.isDebugEnabled()).thenReturn(true);

    Utils.logIfDebug(logger, "debug-message");

    verify(logger).debug("debug-message");
  }

  @Test
  void testLogIfDebugShouldSkipWhenLoggerIsNotInDebugMode() {
    when(logger.isDebugEnabled()).thenReturn(false);

    Utils.logIfDebug(logger, "debug-message");

    verify(logger, never()).debug("debug-message");
  }

  @Test
  void testLogExecutionInitShouldLogProcessAndParameters() {
    when(logger.isDebugEnabled()).thenReturn(true);

    Utils.logExecutionInit(Map.of("ModuleID", "module-1", "Name", "TestName"), logger);

    verify(logger).debug("Executing process");
    verify(logger).debug("Parameter: ModuleID = module-1");
    verify(logger).debug("Parameter: Name = TestName");
  }

  @Test
  void testIsControlTypeShouldReturnExpectedValues() {
    assertTrue(Utils.isControlType(Utils.CONTROL_TYPES.get(0)));
    assertFalse(Utils.isControlType("UNKNOWN_TYPE"));
  }

  @Test
  void testIsCodeIndexFileShouldReturnExpectedValues() {
    assertTrue(Utils.isCodeIndexFile(Utils.FILE_TYPE_COPDEV_CI));
    assertFalse(Utils.isCodeIndexFile("OTHER"));
    assertFalse(Utils.isCodeIndexFile(null));
  }

  @Test
  void testValidateAppAndFileTypeShouldThrowForIncompatibleCombination() {
    OBException exception = assertThrows(OBException.class,
        () -> Utils.validateAppAndFileType("NON_CONTROL", Utils.FILE_TYPE_COPDEV_CI));

    assertEquals("Incompatible app type and file type", exception.getMessage());
  }

  @Test
  void testValidateAppAndFileTypeShouldAcceptCompatibleCombination() {
    assertDoesNotThrow(() -> Utils.validateAppAndFileType(Utils.CONTROL_TYPES.get(0), Utils.FILE_TYPE_COPDEV_CI));
    assertDoesNotThrow(() -> Utils.validateAppAndFileType("NON_CONTROL", "OTHER"));
  }

  @Test
  void testGetModuleByJavaPackageShouldThrowWhenJavaPackageIsNull() {
    OBException exception = assertThrows(OBException.class, () -> Utils.getModuleByJavaPackage(null));

    assertEquals("Java package cannot be null", exception.getMessage());
  }

  @Test
  void testGetModuleByIDShouldThrowWhenIdIsNull() {
    OBException exception = assertThrows(OBException.class, () -> Utils.getModuleByID(null));

    assertEquals("ID cannot be null", exception.getMessage());
  }

  @Test
  void testGetDataPackageShouldReturnFirstDataPackage() {
    when(module.isInDevelopment()).thenReturn(true);
    when(module.getDataPackageList()).thenReturn(List.of(dataPackage));

    DataPackage result = Utils.getDataPackage(module);

    assertNotNull(result);
    assertEquals(dataPackage, result);
  }

  @Test
  void testGetDataPackageShouldThrowWhenModuleIsNotInDevelopment() {
    when(module.isInDevelopment()).thenReturn(false);
    when(module.getName()).thenReturn("Test Module");

    OBException exception = assertThrows(OBException.class, () -> Utils.getDataPackage(module));

    assertEquals("Module Test Module is not in development", exception.getMessage());
  }

  @Test
  void testGetDataPackageShouldThrowWhenModuleHasNoDataPackage() {
    when(module.isInDevelopment()).thenReturn(true);
    when(module.getName()).thenReturn("Test Module");
    when(module.getDataPackageList()).thenReturn(List.of());

    OBException exception = assertThrows(OBException.class, () -> Utils.getDataPackage(module));

    assertEquals("Module Test Module has no data package", exception.getMessage());
  }
}
