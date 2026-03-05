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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDependency;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link AddModuleDependency}.
 */
@ExtendWith(MockitoExtension.class)
class AddModuleDependencyTest {

  private static final String DEPENDS_ON_MODULE_ID = "DependsOnModuleID";
  private static final String DEPENDS_ON_JAVA_PACKAGE = "DependsOnJavaPackage";
  private static final String DEPENDS_MODULE_ID = "dependsModule123";
  private static final String DEPENDS_PACKAGE = "com.test.dependency";

  @InjectMocks
  private AddModuleDependency addModuleDependency;

  @Mock private OBDal obDal;
  @Mock private OBProvider obProvider;
  @Mock private OBContext obContext;
  @Mock private Module module;
  @Mock private Module dependsOnModule;
  @Mock private ModuleDependency moduleDependency;
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
  void testGetWithMissingModuleIDShouldReturnError() {
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("ModuleID parameter is required"));
  }

  @Test
  void testGetWithEmptyModuleIDShouldReturnError() {
    parameters.put(MODULE_ID, "");
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("ModuleID parameter is required"));
  }

  @Test
  void testGetWithMissingBothDependsOnParamsShouldReturnError() {
    parameters.put(MODULE_ID, MODULE_123);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Either DependsOnModuleID or DependsOnJavaPackage"));
  }

  @Test
  void testGetWithValidModuleIdDependencyShouldCreateDependency() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);

    setupSuccessfulScenario();
    when(obDal.get(Module.class, DEPENDS_MODULE_ID)).thenReturn(dependsOnModule);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal).save(any(ModuleDependency.class));
    verify(obDal).flush();
  }

  @Test
  void testGetWithJavaPackageDependencyShouldCreateDependency() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_JAVA_PACKAGE, DEPENDS_PACKAGE);

    setupSuccessfulScenario();
    utilsMock.when(() -> Utils.getModuleByJavaPackage(DEPENDS_PACKAGE)).thenReturn(dependsOnModule);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetWithNonExistentDependsOnModuleIDShouldReturnError() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_MODULE_ID, "nonexistent");

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Module.class, "nonexistent")).thenReturn(null);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("not found"));
  }

  @Test
  void testGetWithExistingDependencyShouldReturnError() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Module.class, DEPENDS_MODULE_ID)).thenReturn(dependsOnModule);

    OBCriteria<ModuleDependency> depCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(ModuleDependency.class)).thenReturn(depCrit);
    when(depCrit.add(any())).thenReturn(depCrit);
    when(depCrit.setMaxResults(anyInt())).thenReturn(depCrit);
    when(depCrit.uniqueResult()).thenReturn(moduleDependency);

    when(module.getJavaPackage()).thenReturn("com.test.module");
    when(dependsOnModule.getJavaPackage()).thenReturn(DEPENDS_PACKAGE);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("already exists"));
  }

  @Test
  void testGetWithVersionParametersShouldIncludeInMessage() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);
    parameters.put("FirstVersion", "2.0.0");
    parameters.put("LastVersion", "3.0.0");

    setupSuccessfulScenario();
    when(obDal.get(Module.class, DEPENDS_MODULE_ID)).thenReturn(dependsOnModule);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertTrue(responseVars.get(MESSAGE).contains("2.0.0"));
  }

  @Test
  void testGetWithIsIncludedShouldSetFlag() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);
    parameters.put("IsIncluded", "true");

    setupSuccessfulScenario();
    when(obDal.get(Module.class, DEPENDS_MODULE_ID)).thenReturn(dependsOnModule);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetWithCustomEnforcementShouldProcess() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(DEPENDS_ON_MODULE_ID, DEPENDS_MODULE_ID);
    parameters.put("Enforcement", "MINOR");

    setupSuccessfulScenario();
    when(obDal.get(Module.class, DEPENDS_MODULE_ID)).thenReturn(dependsOnModule);

    addModuleDependency.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @SuppressWarnings("unchecked")
  private void setupSuccessfulScenario() {
    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);

    when(module.getJavaPackage()).thenReturn("com.test.module");
    when(dependsOnModule.getJavaPackage()).thenReturn(DEPENDS_PACKAGE);
    when(dependsOnModule.getName()).thenReturn("Dependency Module");

    OBCriteria<ModuleDependency> depCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(ModuleDependency.class)).thenReturn(depCrit);
    when(depCrit.add(any())).thenReturn(depCrit);
    when(depCrit.setMaxResults(anyInt())).thenReturn(depCrit);
    when(depCrit.uniqueResult()).thenReturn(null);

    when(obDal.get(Client.class, "0")).thenReturn(client);
    when(obDal.get(Organization.class, "0")).thenReturn(organization);
    when(obProvider.get(ModuleDependency.class)).thenReturn(moduleDependency);
    when(obContext.getUser()).thenReturn(user);
  }
}
