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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.ui.Tab;

/**
 * Unit tests for {@link SetTabFilter}.
 */
@ExtendWith(MockitoExtension.class)
class SetTabFilterTest {

  private static final String TAB_ID = "TabID";
  private static final String WHERE_CLAUSE = "WhereClause";
  private static final String HQL_WHERE_CLAUSE = "HQLWhereClause";
  private static final String ORDER_BY_CLAUSE = "OrderByClause";
  private static final String TEST_TAB_ID = "tab123";
  private static final String TEST_WHERE = "e.iscourse = 'Y'";
  private static final String TEST_TAB_NAME = TEST_TAB_NAME;
  private static final String TEST_HQL_WHERE = TEST_HQL_WHERE;
  private static final String TEST_ORDER_BY = TEST_ORDER_BY;

  @InjectMocks
  private SetTabFilter setTabFilter;

  @Mock private OBDal obDal;
  @Mock private OBContext obContext;
  @Mock private Tab tab;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBContext> obContextMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obContextMock = mockStatic(OBContext.class);
    messageMock = mockStatic(OBMessageUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obContextMock.when(OBContext::getOBContext).thenReturn(obContext);

    parameters = new HashMap<>();
    responseVars = new HashMap<>();
  }

  @AfterEach
  void tearDown() {
    obDalMock.close();
    obContextMock.close();
    messageMock.close();
  }

  @Test
  void testGetWithMissingTabIDShouldReturnError() {
    parameters.put(WHERE_CLAUSE, TEST_WHERE);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("TabID parameter is required"));
  }

  @Test
  void testGetWithEmptyTabIDShouldReturnError() {
    parameters.put(TAB_ID, "");
    parameters.put(WHERE_CLAUSE, TEST_WHERE);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("TabID parameter is required"));
  }

  @Test
  void testGetWithMissingWhereClauseShouldReturnError() {
    parameters.put(TAB_ID, TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("WhereClause parameter is required"));
  }

  @Test
  void testGetWithEmptyWhereClauseShouldReturnError() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, "");

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("WhereClause parameter is required"));
  }

  @Test
  void testGetWithNonExistentTabShouldReturnError() {
    parameters.put(TAB_ID, "nonexistent");
    parameters.put(WHERE_CLAUSE, TEST_WHERE);

    when(obDal.get(Tab.class, "nonexistent")).thenReturn(null);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(ERROR).contains("not found"));
  }

  @Test
  void testGetWithValidParametersShouldSetFilter() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, TEST_WHERE);

    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);
    when(tab.getName()).thenReturn(TEST_TAB_NAME);
    when(tab.getId()).thenReturn(TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    assertTrue(responseVars.get(MESSAGE).contains("Filter set"));

    verify(tab).setSQLWhereClause(TEST_WHERE);
    verify(obDal).save(tab);
    verify(obDal).flush();
  }

  @Test
  void testGetWithHQLWhereClauseShouldSetBoth() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, TEST_WHERE);
    parameters.put(HQL_WHERE_CLAUSE, TEST_HQL_WHERE);

    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);
    when(tab.getName()).thenReturn(TEST_TAB_NAME);
    when(tab.getId()).thenReturn(TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    verify(tab).setSQLWhereClause(TEST_WHERE);
    verify(tab).setHqlwhereclause(TEST_HQL_WHERE);
  }

  @Test
  void testGetWithOrderByClauseShouldSetIt() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, TEST_WHERE);
    parameters.put(ORDER_BY_CLAUSE, TEST_ORDER_BY);

    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);
    when(tab.getName()).thenReturn(TEST_TAB_NAME);
    when(tab.getId()).thenReturn(TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    verify(tab).setSQLOrderByClause(TEST_ORDER_BY);
  }

  @Test
  void testGetWithAllOptionalParametersShouldSetAll() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, TEST_WHERE);
    parameters.put(HQL_WHERE_CLAUSE, TEST_HQL_WHERE);
    parameters.put(ORDER_BY_CLAUSE, TEST_ORDER_BY);

    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);
    when(tab.getName()).thenReturn(TEST_TAB_NAME);
    when(tab.getId()).thenReturn(TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(tab).setSQLWhereClause(TEST_WHERE);
    verify(tab).setHqlwhereclause(TEST_HQL_WHERE);
    verify(tab).setSQLOrderByClause(TEST_ORDER_BY);
    verify(obDal).save(tab);
    verify(obDal).flush();
  }

  @Test
  void testGetWithoutOptionalParamsShouldNotSetThem() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, TEST_WHERE);

    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);
    when(tab.getName()).thenReturn(TEST_TAB_NAME);
    when(tab.getId()).thenReturn(TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(MESSAGE));
    verify(tab).setSQLWhereClause(TEST_WHERE);
    verify(tab, never()).setHqlwhereclause(org.mockito.ArgumentMatchers.anyString());
    verify(tab, never()).setSQLOrderByClause(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void testGetResponseContainsWhereClause() {
    parameters.put(TAB_ID, TEST_TAB_ID);
    parameters.put(WHERE_CLAUSE, TEST_WHERE);

    when(obDal.get(Tab.class, TEST_TAB_ID)).thenReturn(tab);
    when(tab.getName()).thenReturn(TEST_TAB_NAME);
    when(tab.getId()).thenReturn(TEST_TAB_ID);

    setTabFilter.get(parameters, responseVars);

    assertTrue(responseVars.get(MESSAGE).contains(TEST_WHERE));
  }
}
