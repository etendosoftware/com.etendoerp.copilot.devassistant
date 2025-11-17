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

import static com.etendoerp.copilot.devassistant.TestConstants.JAVA_PACKAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.KEYWORD;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
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
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.module.Module;

/**
 * Unit tests for {@link JavaPackageRetriever}.
 *
 * <p>This suite verifies the behavior of the get(Map, Map) method for multiple
 * input scenarios, ensuring that:
 * <ul>
 *   <li>A valid keyword retrieves the expected java package list and populates the
 *       "info" key in the response map.</li>
 *   <li>Null or missing parameters populate the "error" key without setting "info".</li>
 *   <li>Empty, partial, case-insensitive, numeric and special-character keywords are handled.</li>
 *   <li>Criteria interactions with OBDal (createCriteria, add, list) are invoked as expected.</li>
 *   <li>Null java packages and duplicated package values are handled gracefully.</li>
 *   <li>Input parameter maps are not mutated by the method under test.</li>
 * </ul>
 *
 * <p>Mockito is used to mock OBDal static access and DAL components to isolate the
 * unit under test from the persistence layer.
 */
@ExtendWith(MockitoExtension.class)
class JavaPackageRetrieverTest {

  @InjectMocks
  private JavaPackageRetriever javaPackageRetriever;

  @Mock
  private OBDal obDal;

  @Mock
  private OBCriteria<Module> moduleCriteria;

  @Mock
  private Module module1;

  @Mock
  private Module module2;

  @Mock
  private Module module3;

  private MockedStatic<OBDal> obDalMock;

  private Map<String, String> parameters;
  private Map<String, String> responseVars;

  /**
   * Initializes the static OBDal mock and resets test input/output maps before
   * each test, ensuring isolation between test cases.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obDalMock.when(OBDal::getInstance).thenReturn(obDal);

    parameters = new HashMap<>();
    responseVars = new HashMap<>();
  }

  /**
   * Closes the static OBDal mock after each test to avoid cross-test interference.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
  }

  /**
   * Verifies that a valid keyword returns a comma-separated list of matching
   * java packages and sets the "info" key while avoiding the "error" key.
   */
  @Test
  void testGetWithValidKeywordShouldReturnJavaPackages() {
    addKeyword("copilot");

    List<Module> moduleList = Arrays.asList(module1, module2, module3);
    setupCriteriaReturning(moduleList);

    mockModule(module1, JAVA_PACKAGE);
    mockModule(module2, "com.etendoerp.copilot.core");
    mockModule(module3, "com.etendoerp.copilot.assistant");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    String result = responseVars.get("info");
    assertNotNull(result);

    assertEquals(
        "com.etendoerp.copilot, com.etendoerp.copilot.core, com.etendoerp.copilot.assistant",
        result
    );
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures that when only one module matches, the single java package is returned
   * as the "info" value.
   */
  @Test
  void testGetWithSingleModuleFoundShouldReturnOnePackage() {
    addKeyword("warehouse");

    List<Module> moduleList = Collections.singletonList(module1);
    setupCriteriaReturning(moduleList);

    mockModule(module1, "org.openbravo.warehouse");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("org.openbravo.warehouse", responseVars.get("info"));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures that when no modules match the search, the "info" value is set to an
   * empty string and no error is reported.
   */
  @Test
  void testGetWithNoModulesFoundShouldReturnEmptyString() {
    addKeyword("nonexistent");
    setupCriteriaReturning(Collections.emptyList());

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("", responseVars.get("info"));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Validates that missing keyword input triggers an error response and does not
   * populate the "info" key.
   */
  @Test
  void testGetWithNullKeywordShouldReturnError() {
    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Missing parameters.", responseVars.get(ERROR));
    assertFalse(responseVars.containsKey("info"));
  }

  /**
   * Verifies that an empty keyword results in a search across all modules, returning
   * all found java packages.
   */
  @Test
  void testGetWithEmptyKeywordShouldSearchAllModules() {
    addKeyword("");

    List<Module> moduleList = Arrays.asList(module1, module2);
    setupCriteriaReturning(moduleList);

    mockModule(module1, "com.test.module1");
    mockModule(module2, "com.test.module2");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("com.test.module1, com.test.module2", responseVars.get("info"));
  }

  /**
   * Confirms that the search is case-insensitive by using an upper-case keyword
   * that still matches the stored package.
   */
  @Test
  void testGetWithCaseInsensitiveKeywordShouldFindModules() {
    addKeyword("COPILOT");

    List<Module> moduleList = Collections.singletonList(module1);
    setupCriteriaReturning(moduleList);

    mockModule(module1, JAVA_PACKAGE);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals(JAVA_PACKAGE, responseVars.get("info"));
    verify(moduleCriteria).add(any());
  }

  /**
   * Checks that partial keyword matching returns all modules whose packages contain
   * the provided fragment.
   */
  @Test
  void testGetWithPartialKeywordShouldFindMatchingModules() {
    addKeyword("cop");

    List<Module> moduleList = Arrays.asList(module1, module2);
    setupCriteriaReturning(moduleList);

    mockModule(module1, JAVA_PACKAGE);
    mockModule(module2, "com.etendoerp.copilot.dev");

    javaPackageRetriever.get(parameters, responseVars);

    String result = responseVars.get("info");
    assertTrue(result.contains(JAVA_PACKAGE));
    assertTrue(result.contains("com.etendoerp.copilot.dev"));
  }

  /**
   * Ensures that keywords containing special characters do not break the search and
   * still produce an "info" response.
   */
  @Test
  void testGetWithSpecialCharactersInKeywordShouldSearch() {
    addKeyword("test-module");

    List<Module> moduleList = Collections.singletonList(module1);
    setupCriteriaReturning(moduleList);

    mockModule(module1, "com.test.module");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertFalse(responseVars.containsKey(ERROR));
  }

  /**
   * Ensures surrounding whitespace in the keyword is trimmed and does not affect
   * the ability to find matching modules.
   */
  @Test
  void testGetWithWhitespaceKeywordShouldSearch() {
    addKeyword("  copilot  ");

    List<Module> moduleList = Collections.singletonList(module1);
    setupCriteriaReturning(moduleList);

    mockModule(module1, JAVA_PACKAGE);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals(JAVA_PACKAGE, responseVars.get("info"));
  }

  /**
   * Verifies interactions with OBDal criteria: creating criteria, adding a filter,
   * and listing results.
   */
  @Test
  void testGetShouldUseCriteriaCorrectly() {
    addKeyword("test");

    List<Module> moduleList = Collections.emptyList();
    setupCriteriaReturning(moduleList);

    javaPackageRetriever.get(parameters, responseVars);

    verify(obDal).createCriteria(Module.class);
    verify(moduleCriteria).add(any());
    verify(moduleCriteria).list();
  }

  /**
   * Ensures that null java package values in some modules do not cause failures and
   * the method still returns a non-null "info" string.
   */
  @Test
  void testGetWithNullJavaPackageShouldHandleGracefully() {
    addKeyword("test");

    List<Module> moduleList = Arrays.asList(module1, module2);
    setupCriteriaReturning(moduleList);

    mockModule(module1, "com.etendoerp.test");
    mockModule(module2, null);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertNotNull(responseVars.get("info"));
  }

  /**
   * Validates that duplicate package names across different modules are included in
   * the resulting list without being de-duplicated.
   */
  @Test
  void testGetWithMultipleModulesWithSamePackageShouldListAll() {
    addKeyword("copilot");

    List<Module> moduleList = Arrays.asList(module1, module2);
    setupCriteriaReturning(moduleList);

    mockModule(module1, JAVA_PACKAGE);
    mockModule(module2, JAVA_PACKAGE);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("com.etendoerp.copilot, com.etendoerp.copilot", responseVars.get("info"));
  }

  /**
   * Asserts that the method under test does not mutate the provided input parameters
   * map.
   */
  @Test
  void testGetShouldNotModifyInputParameters() {
    addKeyword("test");
    Map<String, String> original = new HashMap<>(parameters);

    setupCriteriaReturning(Collections.emptyList());

    javaPackageRetriever.get(parameters, responseVars);

    assertEquals(original, parameters);
  }

  /**
   * Ensures mutual exclusion between "info" and "error" keys depending on input
   * validity: only one of them should be present after execution.
   */
  @Test
  void testGetShouldOnlySetInfoOrErrorNotBoth() {
    addKeyword("test");

    setupCriteriaReturning(Collections.emptyList());
    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertFalse(responseVars.containsKey(ERROR));

    responseVars.clear();
    parameters.clear();

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertFalse(responseVars.containsKey("info"));
  }

  /**
   * Confirms that numeric keywords are supported and matching modules are returned
   * correctly.
   */
  @Test
  void testGetWithNumericKeywordShouldSearch() {
    addKeyword("123");

    List<Module> moduleList = Collections.singletonList(module1);
    setupCriteriaReturning(moduleList);

    mockModule(module1, "com.module123");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("com.module123", responseVars.get("info"));
  }

  /**
   * Adds a KEYWORD parameter to the test input map.
   */
  private void addKeyword(String keyword) {
    parameters.put(KEYWORD, keyword);
  }

  /**
   * Mocks OBDal criteria behavior for a given list of modules.
   *
   * @param moduleList list to be returned by moduleCriteria.list()
   */
  private void setupCriteriaReturning(List<Module> moduleList) {
    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);
  }

  /**
   * Mocks a Module instance with the specified java package.
   *
   * @param module      mocked Module
   * @param javaPackage java package value to return (nullable)
   */
  private void mockModule(Module module, String javaPackage) {
    when(module.getJavaPackage()).thenReturn(javaPackage);
  }
}
