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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
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
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Element;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link SyncTerms}.
 */
@ExtendWith(MockitoExtension.class)
class SyncTermsTest {

  @InjectMocks
  private SyncTerms syncTerms;

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria<Module> moduleCriteria;

  @Mock
  private OBCriteria<Element> elementCriteria;

  @Mock
  private Module module;

  @Mock
  private Element element;

  @Mock
  private OBError processResult;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<Utils> utilsMock;

  private Map<String, String> params;
  private Map<String, String> responseVars;

  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    utilsMock = mockStatic(Utils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);

    params = new HashMap<>();
    responseVars = new HashMap<>();
  }

  @AfterEach
  void tearDown() {
    obDalMock.close();
    utilsMock.close();
  }

  @Test
  void testGetShouldReturnMessageAndNormalizeElementTextsOnSuccess() {
    utilsMock.when(() -> Utils.execPInstanceProcess("172", "0")).thenReturn(processResult);
    when(processResult.getType()).thenReturn("Success");
    when(processResult.getTitle()).thenReturn("Success");
    when(processResult.getMessage()).thenReturn("Terms synchronized");

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any(Restriction.class))).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(List.of(module));

    when(obDal.createCriteria(Element.class)).thenReturn(elementCriteria);
    when(elementCriteria.add(any(Restriction.class))).thenReturn(elementCriteria);
    when(elementCriteria.list()).thenReturn(List.of(element));

    when(element.getName()).thenReturn("test_element");
    when(element.getPrintText()).thenReturn("test_print");

    syncTerms.get(params, responseVars);

    assertEquals("Success - Terms synchronized", responseVars.get("message"));
    verify(element).setName("Test element");
    verify(element).setPrintText("Test print");
    verify(obDal).save(element);
    verify(obDal).flush();
  }

  @Test
  void testGetShouldStoreErrorWhenProcessExecutionFails() {
    utilsMock.when(() -> Utils.execPInstanceProcess("172", "0"))
        .thenThrow(new RuntimeException("Process failed"));

    syncTerms.get(params, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Process failed", responseVars.get("error"));
    verify(obDal, never()).flush();
  }
}
