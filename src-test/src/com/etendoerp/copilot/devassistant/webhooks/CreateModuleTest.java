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

import static com.etendoerp.copilot.devassistant.TestConstants.DB_PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_PREFIX;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Unit tests for {@link CreateModule}.
 */
@ExtendWith(MockitoExtension.class)
class CreateModuleTest extends BaseWebhookTest {

  private static final String JAVA_PACKAGE = "JavaPackage";
  private static final String TEST_JAVA_PACKAGE = "com.test.newmodule";
  private static final String TEST_MODULE_NAME = "Test Module";

  @InjectMocks
  private CreateModule createModule;

  @Mock private Module module;
  @Mock private ModuleDBPrefix moduleDBPrefix;
  @Mock private DataPackage dataPackage;

  @Test
  void testGetWithMissingNameShouldReturnError() {
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Name parameter is required"));
  }

  @Test
  void testGetWithMissingJavaPackageShouldReturnError() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("JavaPackage parameter is required"));
  }

  @Test
  void testGetWithMissingDBPrefixShouldReturnError() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("DBPrefix parameter is required"));
  }

  @Test
  void testGetWithEmptyNameShouldReturnError() {
    parameters.put("Name", "");
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Name parameter is required"));
  }

  @Test
  void testGetWithValidParametersShouldCreateModule() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    setupSuccessfulScenario();

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(MESSAGE).contains("Module created successfully"));
    verify(obDal, times(3)).save(any());
    verify(obDal).flush();
  }

  @Test
  void testGetWithExistingJavaPackageShouldReturnError() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    OBCriteria<Module> moduleCrit = mockCriteria(Module.class);
    when(moduleCrit.uniqueResult()).thenReturn(module);

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("already exists"));
  }

  @Test
  void testGetWithExistingDBPrefixShouldReturnError() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    OBCriteria<Module> moduleCrit = mockCriteria(Module.class);
    when(moduleCrit.uniqueResult()).thenReturn(null);

    OBCriteria<ModuleDBPrefix> prefixCrit = mockCriteria(ModuleDBPrefix.class);
    when(prefixCrit.uniqueResult()).thenReturn(moduleDBPrefix);

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("already used"));
  }

  @Test
  void testGetWithTemplateTypeShouldNotCreateDBPrefix() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);
    parameters.put("Type", "T");

    setupSuccessfulScenario();

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal, times(2)).save(any());
  }

  @Test
  void testGetWithAllOptionalParametersShouldProcess() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);
    parameters.put("Description", "Custom description");
    parameters.put("Version", "2.0.0");
    parameters.put("Author", "Test Author");
    parameters.put("Type", "M");

    setupSuccessfulScenario();

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetShouldNormalizePrefixToUpperCase() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, "lowercase");

    setupSuccessfulScenario();

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetWithExceptionDuringCreationShouldReturnError() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    when(obDal.get(Client.class, "0")).thenThrow(new RuntimeException("DB error"));

    createModule.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
  }

  @SuppressWarnings("unchecked")
  private void setupSuccessfulScenario() {
    OBCriteria<Module> moduleCrit = mockCriteria(Module.class);
    lenient().when(moduleCrit.uniqueResult()).thenReturn(null);

    OBCriteria<ModuleDBPrefix> prefixCrit = mockCriteria(ModuleDBPrefix.class);
    lenient().when(prefixCrit.uniqueResult()).thenReturn(null);

    lenient().when(obDal.get(Client.class, "0")).thenReturn(client);
    lenient().when(obDal.get(Organization.class, "0")).thenReturn(organization);
    lenient().when(obProvider.get(Module.class)).thenReturn(module);
    lenient().when(obProvider.get(ModuleDBPrefix.class)).thenReturn(moduleDBPrefix);
    lenient().when(obProvider.get(DataPackage.class)).thenReturn(dataPackage);
    lenient().when(obContext.getUser()).thenReturn(user);
    lenient().when(module.getId()).thenReturn("newmod123");
    lenient().when(module.getName()).thenReturn(TEST_MODULE_NAME);
    lenient().when(module.getJavaPackage()).thenReturn(TEST_JAVA_PACKAGE);
  }
}
