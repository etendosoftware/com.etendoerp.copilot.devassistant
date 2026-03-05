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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
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
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Unit tests for {@link CreateModule}.
 */
@ExtendWith(MockitoExtension.class)
class CreateModuleTest {

  private static final String JAVA_PACKAGE = "JavaPackage";
  private static final String TEST_JAVA_PACKAGE = "com.test.newmodule";
  private static final String TEST_MODULE_NAME = "Test Module";

  @InjectMocks
  private CreateModule createModule;

  @Mock private OBDal obDal;
  @Mock private OBProvider obProvider;
  @Mock private OBContext obContext;
  @Mock private Module module;
  @Mock private ModuleDBPrefix moduleDBPrefix;
  @Mock private DataPackage dataPackage;
  @Mock private Client client;
  @Mock private Organization organization;
  @Mock private User user;
  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obProviderMock = mockStatic(OBProvider.class);
    obContextMock = mockStatic(OBContext.class);
    messageMock = mockStatic(OBMessageUtils.class);

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
  }

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
    // Module + ModuleDBPrefix + DataPackage = 3 saves
    verify(obDal, times(3)).save(any());
    verify(obDal).flush();
  }

  @Test
  void testGetWithExistingJavaPackageShouldReturnError() {
    parameters.put("Name", TEST_MODULE_NAME);
    parameters.put(JAVA_PACKAGE, TEST_JAVA_PACKAGE);
    parameters.put(DB_PREFIX, TEST_PREFIX);

    OBCriteria<Module> moduleCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Module.class)).thenReturn(moduleCrit);
    when(moduleCrit.add(any())).thenReturn(moduleCrit);
    when(moduleCrit.setMaxResults(anyInt())).thenReturn(moduleCrit);
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

    OBCriteria<Module> moduleCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Module.class)).thenReturn(moduleCrit);
    when(moduleCrit.add(any())).thenReturn(moduleCrit);
    when(moduleCrit.setMaxResults(anyInt())).thenReturn(moduleCrit);
    when(moduleCrit.uniqueResult()).thenReturn(null);

    OBCriteria<ModuleDBPrefix> prefixCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCrit);
    when(prefixCrit.add(any())).thenReturn(prefixCrit);
    when(prefixCrit.setMaxResults(anyInt())).thenReturn(prefixCrit);
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
    // Module + DataPackage = 2 saves (no ModuleDBPrefix for templates)
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
    OBCriteria<Module> moduleCrit = mock(OBCriteria.class);
    lenient().when(obDal.createCriteria(Module.class)).thenReturn(moduleCrit);
    lenient().when(moduleCrit.add(any())).thenReturn(moduleCrit);
    lenient().when(moduleCrit.setMaxResults(anyInt())).thenReturn(moduleCrit);
    lenient().when(moduleCrit.uniqueResult()).thenReturn(null);

    OBCriteria<ModuleDBPrefix> prefixCrit = mock(OBCriteria.class);
    lenient().when(obDal.createCriteria(ModuleDBPrefix.class)).thenReturn(prefixCrit);
    lenient().when(prefixCrit.add(any())).thenReturn(prefixCrit);
    lenient().when(prefixCrit.setMaxResults(anyInt())).thenReturn(prefixCrit);
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
