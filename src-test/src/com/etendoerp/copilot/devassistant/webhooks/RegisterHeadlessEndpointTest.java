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
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.etendorx.data.OpenAPITab;
import com.etendoerp.openapi.data.OpenAPIRequest;

/**
 * Unit tests for {@link RegisterHeadlessEndpoint}.
 */
@ExtendWith(MockitoExtension.class)
class RegisterHeadlessEndpointTest {

  private static final String REQUEST_NAME = "RequestName";
  private static final String TAB_ID = "TabID";
  private static final String TABLE_NAME = "TableName";
  private static final String TEST_REQUEST = "MyProducts";
  private static final String TEST_TAB_ID = "tab123";

  @InjectMocks
  private RegisterHeadlessEndpoint registerHeadlessEndpoint;

  @Mock private OBDal obDal;
  @Mock private OBProvider obProvider;
  @Mock private OBContext obContext;
  @Mock private Module module;
  @Mock private Tab tab;
  @Mock private Table table;
  @Mock private OpenAPIRequest openAPIRequest;
  @Mock private OpenAPITab openAPITab;
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
  void testGetWithMissingRequestNameShouldReturnError() {
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, TEST_TAB_ID);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("RequestName parameter is required"));
  }

  @Test
  void testGetWithMissingModuleIDShouldReturnError() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(TAB_ID, TEST_TAB_ID);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("ModuleID parameter is required"));
  }

  @Test
  void testGetWithMissingBothTabAndTableShouldReturnError() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("Either TabID or TableName"));
  }

  @Test
  void testGetWithEmptyRequestNameShouldReturnError() {
    parameters.put(REQUEST_NAME, "");
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, TEST_TAB_ID);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("RequestName parameter is required"));
  }

  @Test
  void testGetWithValidParametersByTabIDShouldRegisterEndpoint() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, TEST_TAB_ID);

    setupSuccessfulScenario();
    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(MESSAGE).contains(TEST_REQUEST));
    // OpenAPIRequest + OpenAPITab = 2 saves
    verify(obDal, times(2)).save(any());
    verify(obDal).flush();
  }

  @Test
  void testGetWithValidParametersByTableNameShouldResolveTab() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_NAME, "m_product");

    setupSuccessfulScenario();
    utilsMock.when(() -> Utils.getTableByDBName("m_product")).thenReturn(table);

    OBCriteria<Tab> tabCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Tab.class)).thenReturn(tabCrit);
    when(tabCrit.add(any())).thenReturn(tabCrit);
    when(tabCrit.addOrderBy(any(), any(Boolean.class))).thenReturn(tabCrit);
    when(tabCrit.setMaxResults(anyInt())).thenReturn(tabCrit);
    when(tabCrit.uniqueResult()).thenReturn(tab);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetWithNonExistentTabIDShouldReturnError() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, "nonexistent");

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Tab.class, "nonexistent")).thenReturn(null);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("not found"));
  }

  @Test
  void testGetWithExistingEndpointShouldReturnError() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, TEST_TAB_ID);

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);

    OBCriteria<OpenAPIRequest> reqCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(OpenAPIRequest.class)).thenReturn(reqCrit);
    when(reqCrit.add(any())).thenReturn(reqCrit);
    when(reqCrit.setMaxResults(anyInt())).thenReturn(reqCrit);
    when(reqCrit.uniqueResult()).thenReturn(openAPIRequest);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("already exists"));
  }

  @Test
  void testGetWithTableNameNoTabFoundShouldReturnError() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TABLE_NAME, "no_tab_table");

    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);
    utilsMock.when(() -> Utils.getTableByDBName("no_tab_table")).thenReturn(table);

    OBCriteria<Tab> tabCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Tab.class)).thenReturn(tabCrit);
    when(tabCrit.add(any())).thenReturn(tabCrit);
    when(tabCrit.addOrderBy(any(), any(Boolean.class))).thenReturn(tabCrit);
    when(tabCrit.setMaxResults(anyInt())).thenReturn(tabCrit);
    when(tabCrit.uniqueResult()).thenReturn(null);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("No tab found"));
  }

  @Test
  void testGetWithDescriptionAndTypeShouldProcess() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put("Description", "Custom endpoint description");
    parameters.put("Type", "W");

    setupSuccessfulScenario();
    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
  }

  @Test
  void testGetResponseContainsURL() {
    parameters.put(REQUEST_NAME, TEST_REQUEST);
    parameters.put(MODULE_ID, MODULE_123);
    parameters.put(TAB_ID, TEST_TAB_ID);

    setupSuccessfulScenario();
    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);

    registerHeadlessEndpoint.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertTrue(responseVars.get(MESSAGE).contains("/sws/com.etendoerp.etendorx.datasource/" + TEST_REQUEST));
  }

  @SuppressWarnings("unchecked")
  private void setupSuccessfulScenario() {
    utilsMock.when(() -> Utils.getModuleByID(MODULE_123)).thenReturn(module);

    OBCriteria<OpenAPIRequest> reqCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(OpenAPIRequest.class)).thenReturn(reqCrit);
    when(reqCrit.add(any())).thenReturn(reqCrit);
    when(reqCrit.setMaxResults(anyInt())).thenReturn(reqCrit);
    when(reqCrit.uniqueResult()).thenReturn(null);

    when(obDal.get(Client.class, "0")).thenReturn(client);
    when(obDal.get(Organization.class, "0")).thenReturn(organization);
    when(obProvider.get(OpenAPIRequest.class)).thenReturn(openAPIRequest);
    when(obProvider.get(OpenAPITab.class)).thenReturn(openAPITab);
    when(obContext.getUser()).thenReturn(user);
    when(openAPIRequest.getId()).thenReturn("req123");
    when(openAPIRequest.getName()).thenReturn(TEST_REQUEST);
    when(tab.getName()).thenReturn("Product");
  }
}
