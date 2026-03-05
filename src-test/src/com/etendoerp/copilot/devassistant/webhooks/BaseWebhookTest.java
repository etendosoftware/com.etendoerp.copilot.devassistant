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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Base class for webhook unit tests. Provides common mock setup for
 * OBDal, OBProvider, OBContext, OBMessageUtils, and Utils static mocks.
 */
abstract class BaseWebhookTest {

  @Mock protected OBDal obDal;
  @Mock protected OBProvider obProvider;
  @Mock protected OBContext obContext;
  @Mock protected Client client;
  @Mock protected Organization organization;
  @Mock protected User user;

  protected MockedStatic<OBDal> obDalMock;
  protected MockedStatic<OBProvider> obProviderMock;
  protected MockedStatic<OBContext> obContextMock;
  protected MockedStatic<OBMessageUtils> messageMock;
  protected MockedStatic<Utils> utilsMock;

  protected Map<String, String> parameters;
  protected Map<String, String> responseVars;

  @BeforeEach
  void baseSetUp() {
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
  void baseTearDown() {
    obDalMock.close();
    obProviderMock.close();
    obContextMock.close();
    messageMock.close();
    utilsMock.close();
  }

  @SuppressWarnings("unchecked")
  protected <T> OBCriteria<T> mockCriteria(Class<T> entityClass) {
    OBCriteria<T> crit = mock(OBCriteria.class);
    when(obDal.createCriteria(entityClass)).thenReturn(crit);
    when(crit.add(any())).thenReturn(crit);
    when(crit.setMaxResults(anyInt())).thenReturn(crit);
    return crit;
  }

  protected void stubClientOrgUser() {
    when(obDal.get(Client.class, "0")).thenReturn(client);
    when(obDal.get(Organization.class, "0")).thenReturn(organization);
    when(obContext.getUser()).thenReturn(user);
  }
}
