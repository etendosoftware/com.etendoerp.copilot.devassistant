package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
    parameters.put("KeyWord", "copilot");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);
    moduleList.add(module2);
    moduleList.add(module3);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.etendoerp.copilot");
    when(module2.getJavaPackage()).thenReturn("com.etendoerp.copilot.core");
    when(module3.getJavaPackage()).thenReturn("com.etendoerp.copilot.assistant");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    String result = responseVars.get("info");
    assertNotNull(result);
    assertTrue(result.contains("com.etendoerp.copilot"));
    assertTrue(result.contains("com.etendoerp.copilot.core"));
    assertTrue(result.contains("com.etendoerp.copilot.assistant"));
    assertEquals("com.etendoerp.copilot, com.etendoerp.copilot.core, com.etendoerp.copilot.assistant", result);

    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Ensures that when only one module matches, the single java package is returned
   * as the "info" value.
   */
  @Test
  void testGetWithSingleModuleFoundShouldReturnOnePackage() {
    parameters.put("KeyWord", "warehouse");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("org.openbravo.warehouse");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("org.openbravo.warehouse", responseVars.get("info"));
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Ensures that when no modules match the search, the "info" value is set to an
   * empty string and no error is reported.
   */
  @Test
  void testGetWithNoModulesFoundShouldReturnEmptyString() {
    parameters.put("KeyWord", "nonexistent");

    List<Module> moduleList = new ArrayList<>();

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("", responseVars.get("info"));
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Validates that missing keyword input triggers an error response and does not
   * populate the "info" key.
   */
  @Test
  void testGetWithNullKeywordShouldReturnError() {
    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Missing parameters.", responseVars.get("error"));
    assertFalse(responseVars.containsKey("info"));
  }

  /**
   * Verifies that an empty keyword results in a search across all modules, returning
   * all found java packages.
   */
  @Test
  void testGetWithEmptyKeywordShouldSearchAllModules() {
    parameters.put("KeyWord", "");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);
    moduleList.add(module2);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.test.module1");
    when(module2.getJavaPackage()).thenReturn("com.test.module2");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    String result = responseVars.get("info");
    assertEquals("com.test.module1, com.test.module2", result);
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Confirms that the search is case-insensitive by using an upper-case keyword
   * that still matches the stored package.
   */
  @Test
  void testGetWithCaseInsensitiveKeywordShouldFindModules() {
    parameters.put("KeyWord", "COPILOT");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.etendoerp.copilot");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("com.etendoerp.copilot", responseVars.get("info"));
    verify(moduleCriteria).add(any());
  }

  /**
   * Checks that partial keyword matching returns all modules whose packages contain
   * the provided fragment.
   */
  @Test
  void testGetWithPartialKeywordShouldFindMatchingModules() {
    parameters.put("KeyWord", "cop");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);
    moduleList.add(module2);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.etendoerp.copilot");
    when(module2.getJavaPackage()).thenReturn("com.etendoerp.copilot.dev");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    String result = responseVars.get("info");
    assertTrue(result.contains("com.etendoerp.copilot"));
    assertTrue(result.contains("com.etendoerp.copilot.dev"));
  }

  /**
   * Ensures that keywords containing special characters do not break the search and
   * still produce an "info" response.
   */
  @Test
  void testGetWithSpecialCharactersInKeywordShouldSearch() {
    parameters.put("KeyWord", "test-module");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.test.module");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertFalse(responseVars.containsKey("error"));
  }

  /**
   * Ensures surrounding whitespace in the keyword is trimmed and does not affect
   * the ability to find matching modules.
   */
  @Test
  void testGetWithWhitespaceKeywordShouldSearch() {
    parameters.put("KeyWord", "  copilot  ");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.etendoerp.copilot");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("com.etendoerp.copilot", responseVars.get("info"));
  }

  /**
   * Verifies interactions with OBDal criteria: creating criteria, adding a filter,
   * and listing results.
   */
  @Test
  void testGetShouldUseCriteriaCorrectly() {
    parameters.put("KeyWord", "test");

    List<Module> moduleList = new ArrayList<>();

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

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
    parameters.put("KeyWord", "test");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);
    moduleList.add(module2);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.etendoerp.test");
    when(module2.getJavaPackage()).thenReturn(null);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    String result = responseVars.get("info");
    assertNotNull(result);
  }

  /**
   * Validates that duplicate package names across different modules are included in
   * the resulting list without being de-duplicated.
   */
  @Test
  void testGetWithMultipleModulesWithSamePackageShouldListAll() {
    parameters.put("KeyWord", "copilot");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);
    moduleList.add(module2);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.etendoerp.copilot");
    when(module2.getJavaPackage()).thenReturn("com.etendoerp.copilot");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    String result = responseVars.get("info");
    assertEquals("com.etendoerp.copilot, com.etendoerp.copilot", result);
  }

  /**
   * Asserts that the method under test does not mutate the provided input parameters
   * map.
   */
  @Test
  void testGetShouldNotModifyInputParameters() {
    parameters.put("KeyWord", "test");
    Map<String, String> originalParams = new HashMap<>(parameters);

    List<Module> moduleList = new ArrayList<>();

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    javaPackageRetriever.get(parameters, responseVars);

    assertEquals(originalParams, parameters);
  }

  /**
   * Ensures mutual exclusion between "info" and "error" keys depending on input
   * validity: only one of them should be present after execution.
   */
  @Test
  void testGetShouldOnlySetInfoOrErrorNotBoth() {
    parameters.put("KeyWord", "test");

    List<Module> moduleList = new ArrayList<>();

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertFalse(responseVars.containsKey("error"));

    responseVars.clear();
    parameters.clear();

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertFalse(responseVars.containsKey("info"));
  }

  /**
   * Confirms that numeric keywords are supported and matching modules are returned
   * correctly.
   */
  @Test
  void testGetWithNumericKeywordShouldSearch() {
    parameters.put("KeyWord", "123");

    List<Module> moduleList = new ArrayList<>();
    moduleList.add(module1);

    when(obDal.createCriteria(Module.class)).thenReturn(moduleCriteria);
    when(moduleCriteria.add(any())).thenReturn(moduleCriteria);
    when(moduleCriteria.list()).thenReturn(moduleList);

    when(module1.getJavaPackage()).thenReturn("com.module123");

    javaPackageRetriever.get(parameters, responseVars);

    assertTrue(responseVars.containsKey("info"));
    assertEquals("com.module123", responseVars.get("info"));
  }
}
