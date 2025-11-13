package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.domain.List;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.Utils;

/**
 * Unit tests for {@link CreateReference} webhook service.
 *
 * This suite validates CreateReference.get behavior:
 * - Parameter validation for ReferenceList, Prefix, NameReference, Help, Description.
 * - Creation and persistence of Reference and associated List items.
 * - Trimming and unique generation of search keys for List items.
 * - Proper use of OBDal save/flush and message localization via OBMessageUtils.
 * - Error handling when persistence throws exceptions.
 *
 * Static components (OBDal, OBProvider, Utils, OBMessageUtils) are mocked to avoid DB access.
 */
@ExtendWith(MockitoExtension.class)
class CreateReferenceTest {

  @InjectMocks
  private CreateReference service;

  @Mock
  private OBDal obDal;

  @Mock
  private OBProvider obProvider;

  @Mock
  private Reference reference;

  @Mock
  private Reference parentReference;

  @Mock
  private Module module;

  @Mock
  private List referenceList;

  private MockedStatic<OBDal> obDalMock;
  private MockedStatic<OBProvider> obProviderMock;
  private MockedStatic<Utils> utilsMock;
  private MockedStatic<OBMessageUtils> messageMock;

  private Map<String, String> requestParams;
  private Map<String, String> responseVars;

  /**
   * Initializes static mocks and request/response maps before each test.
   */
  @BeforeEach
  void setUp() {
    obDalMock = mockStatic(OBDal.class);
    obProviderMock = mockStatic(OBProvider.class);
    utilsMock = mockStatic(Utils.class);
    messageMock = mockStatic(OBMessageUtils.class);

    obDalMock.when(OBDal::getInstance).thenReturn(obDal);
    obProviderMock.when(OBProvider::getInstance).thenReturn(obProvider);

    requestParams = new HashMap<>();
    responseVars = new HashMap<>();
  }

  /**
   * Closes static mocks to avoid interference between tests.
   */
  @AfterEach
  void tearDown() {
    obDalMock.close();
    obProviderMock.close();
    utilsMock.close();
    messageMock.close();
  }

  /**
   * With valid parameters, creates a Reference and 3 List items, persists and flushes, returning success.
   */
  @Test
  void testGetWithValidParametersShouldCreateReferenceSuccessfully() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Reference created successfully", responseVars.get("message"));
    assertFalse(responseVars.containsKey("error"));
    verify(obDal, atLeastOnce()).save(any(Reference.class));
    verify(obDal, times(3)).save(any(List.class)); // 3 items in the list
    verify(obDal).flush();
  }

  /**
   * Missing ReferenceList should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingReferenceListShouldReturnError() {
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("ReferenceList parameter is missing", responseVars.get("error"));
  }

  /**
   * Missing Prefix should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingPrefixShouldReturnError() {
    requestParams.put("ReferenceList", "Item1,Item2,Item3");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Prefix parameter is missing", responseVars.get("error"));
  }

  /**
   * Missing NameReference should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingNameReferenceShouldReturnError() {
    requestParams.put("ReferenceList", "Item1,Item2,Item3");
    requestParams.put("Prefix", "TEST");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("NameReference parameter is missing", responseVars.get("error"));
  }

  /**
   * Missing Help should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingHelpShouldReturnError() {
    requestParams.put("ReferenceList", "Item1,Item2,Item3");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Description", "Test Description");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Help parameter is missing", responseVars.get("error"));
  }

  /**
   * Missing Description should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingDescriptionShouldReturnError() {
    requestParams.put("ReferenceList", "Item1,Item2,Item3");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Description parameter is missing", responseVars.get("error"));
  }

  /**
   * Empty ReferenceList should be treated as missing and return an error.
   */
  @Test
  void testGetWithEmptyReferenceListShouldReturnError() {
    requestParams.put("ReferenceList", "");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("ReferenceList parameter is missing", responseVars.get("error"));
  }

  /**
   * Blank/whitespace parameters should be treated as missing and return an error.
   */
  @Test
  void testGetWithBlankParametersShouldReturnError() {
    requestParams.put("ReferenceList", "   ");
    requestParams.put("Prefix", "   ");
    requestParams.put("NameReference", "   ");
    requestParams.put("Help", "   ");
    requestParams.put("Description", "   ");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("ReferenceList parameter is missing", responseVars.get("error"));
  }

  /**
   * A single item in ReferenceList results in exactly one List entity being saved.
   */
  @Test
  void testGetWithSingleItemShouldCreateOneReferenceListItem() {
    requestParams.put("ReferenceList", "SingleItem");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Reference created successfully", responseVars.get("message"));
    verify(obDal, times(1)).save(any(List.class));
  }

  /**
   * Items with extra spaces are trimmed before being used as names.
   */
  @Test
  void testGetWithItemsWithSpacesShouldTrimItems() {
    requestParams.put("ReferenceList", " Item1 , Item2 , Item3 ");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);

    service.get(requestParams, responseVars);

    verify(obDal, times(3)).save(listCaptor.capture());
    verify(referenceList, times(3)).setName(argThat(name ->
        name.equals("Item1") || name.equals("Item2") || name.equals("Item3")));
  }

  /**
   * Duplicate-like items must result in unique generated search keys.
   */
  @Test
  void testGetWithDuplicateItemsShouldGenerateUniqueSearchKeys() {
    requestParams.put("ReferenceList", "Active,Active Status,Activated");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    ArgumentCaptor<String> searchKeyCaptor = ArgumentCaptor.forClass(String.class);

    service.get(requestParams, responseVars);

    verify(referenceList, times(3)).setSearchKey(searchKeyCaptor.capture());
    java.util.List<String> searchKeys = searchKeyCaptor.getAllValues();

    assertEquals(3, new java.util.HashSet<>(searchKeys).size());
  }

  /**
   * Very short items still generate valid search keys and are persisted.
   */
  @Test
  void testGetWithShortItemsShouldGenerateSearchKeys() {
    requestParams.put("ReferenceList", "A,B,C");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Reference created successfully", responseVars.get("message"));
    verify(obDal, times(3)).save(any(List.class));
  }

  /**
   * Ensures the Reference is initialized with expected properties before saving.
   */
  @Test
  void testGetCreatesReferenceWithCorrectProperties() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    verify(reference).setNewOBObject(true);
    verify(reference).setName("Test Reference");
    verify(reference).setModule(module);
    verify(reference).setParentReference(parentReference);
    verify(reference).setHelpComment("Test Help");
    verify(reference).setDescription("Test Description");
    verify(obDal).save(reference);
  }

  /**
   * Ensures each List item is initialized, linked to Reference and Module, and has name/searchKey.
   */
  @Test
  void testGetCreatesReferenceListItemsWithCorrectProperties() {
    setupValidRequestParams();
    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    verify(referenceList, times(3)).setNewOBObject(true);
    verify(referenceList, times(3)).setReference(reference);
    verify(referenceList, times(3)).setModule(module);
    verify(referenceList, times(3)).setName(anyString());
    verify(referenceList, times(3)).setSearchKey(anyString());
  }

  /**
   * When persistence throws, the error message should be returned in responseVars.
   */
  @Test
  void testGetWhenExceptionOccursShouldReturnError() {
    setupValidRequestParams();

    when(obProvider.get(Reference.class)).thenReturn(reference);
    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);
    when(obDal.get(Reference.class, "17")).thenReturn(parentReference);

    doThrow(new RuntimeException("Database error")).when(obDal).save(any(Reference.class));

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey("error"));
    assertEquals("Database error", responseVars.get("error"));
  }

  /**
   * A long ReferenceList should create and save one List entity per item.
   */
  @Test
  void testGetWithManyItemsShouldCreateAllItems() {
    requestParams.put("ReferenceList", "Item1,Item2,Item3,Item4,Item5,Item6,Item7,Item8,Item9,Item10");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Reference created successfully", responseVars.get("message"));
    verify(obDal, times(10)).save(any(List.class));
  }

  /**
   * Items containing special characters should be handled and persisted correctly.
   */
  @Test
  void testGetWithSpecialCharactersShouldHandleCorrectly() {
    requestParams.put("ReferenceList", "Item-1,Item_2,Item.3");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");

    setupMocksForSuccessfulCreation();

    messageMock.when(() -> OBMessageUtils.messageBD("COPDEV_ReferenceCreated"))
        .thenReturn("Reference created successfully");

    service.get(requestParams, responseVars);

    assertEquals("Reference created successfully", responseVars.get("message"));
    verify(obDal, times(3)).save(any(List.class));
  }


  /**
   * Populates requestParams with a minimal valid payload shared across tests.
   */
  private void setupValidRequestParams() {
    requestParams.put("ReferenceList", "Item1,Item2,Item3");
    requestParams.put("Prefix", "TEST");
    requestParams.put("NameReference", "Test Reference");
    requestParams.put("Help", "Test Help");
    requestParams.put("Description", "Test Description");
  }

  /**
   * Configures common mocks so that entity creation can proceed without errors.
   */
  private void setupMocksForSuccessfulCreation() {
    when(obProvider.get(Reference.class)).thenReturn(reference);
    when(obProvider.get(List.class)).thenReturn(referenceList);

    utilsMock.when(() -> Utils.getModuleByPrefix("TEST")).thenReturn(module);

    when(obDal.get(Reference.class, "17")).thenReturn(parentReference);
    when(reference.getModule()).thenReturn(module);
  }
}
