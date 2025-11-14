package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.TestConstants.DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.ERROR;
import static com.etendoerp.copilot.devassistant.TestConstants.ERR_MISSING_REFERENCE_LIST;
import static com.etendoerp.copilot.devassistant.TestConstants.MESSAGE;
import static com.etendoerp.copilot.devassistant.TestConstants.MSG_REFERENCE_CREATED;
import static com.etendoerp.copilot.devassistant.TestConstants.NAME_REFERENCE;
import static com.etendoerp.copilot.devassistant.TestConstants.PREFIX;
import static com.etendoerp.copilot.devassistant.TestConstants.REFERENCE_CREATED_SUCCESS;
import static com.etendoerp.copilot.devassistant.TestConstants.REFERENCE_LIST;
import static com.etendoerp.copilot.devassistant.TestConstants.REFERENCE_LIST_ITEMS;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_DESCRIPTION;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_HELP;
import static com.etendoerp.copilot.devassistant.TestConstants.TEST_REFERENCE;
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
    setupSuccessfulCreationWithMessage();

    service.get(requestParams, responseVars);

    assertEquals(REFERENCE_CREATED_SUCCESS, responseVars.get(MESSAGE));
    assertFalse(responseVars.containsKey(ERROR));
    verify(obDal, atLeastOnce()).save(any(Reference.class));
    verify(obDal, times(3)).save(any(List.class)); // 3 items in the list
    verify(obDal).flush();
  }

  /**
   * Missing ReferenceList should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingReferenceListShouldReturnError() {
    setupBaseParamsWithoutReferenceList();

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals(ERR_MISSING_REFERENCE_LIST, responseVars.get(ERROR));
  }

  /**
   * Missing Prefix should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingPrefixShouldReturnError() {
    requestParams.put(REFERENCE_LIST, REFERENCE_LIST_ITEMS);
    setupBaseParamsWithoutReferenceList();
    requestParams.remove(PREFIX);

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Prefix parameter is missing", responseVars.get(ERROR));
  }

  /**
   * Missing NameReference should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingNameReferenceShouldReturnError() {
    requestParams.put(REFERENCE_LIST, REFERENCE_LIST_ITEMS);
    setupBaseParamsWithoutReferenceList();
    requestParams.remove(NAME_REFERENCE);

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("NameReference parameter is missing", responseVars.get(ERROR));
  }

  /**
   * Missing Help should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingHelpShouldReturnError() {
    requestParams.put(REFERENCE_LIST, REFERENCE_LIST_ITEMS);
    setupBaseParamsWithoutReferenceList();
    requestParams.remove("Help");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Help parameter is missing", responseVars.get(ERROR));
  }

  /**
   * Missing Description should yield a validation error in responseVars.
   */
  @Test
  void testGetWithMissingDescriptionShouldReturnError() {
    requestParams.put(REFERENCE_LIST, REFERENCE_LIST_ITEMS);
    setupBaseParamsWithoutReferenceList();
    requestParams.remove(DESCRIPTION);

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Description parameter is missing", responseVars.get(ERROR));
  }

  /**
   * Empty ReferenceList should be treated as missing and return an error.
   */
  @Test
  void testGetWithEmptyReferenceListShouldReturnError() {
    setupBaseParamsWithoutReferenceList();
    requestParams.put(REFERENCE_LIST, "");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals(ERR_MISSING_REFERENCE_LIST, responseVars.get(ERROR));
  }

  /**
   * Blank/whitespace parameters should be treated as missing and return an error.
   */
  @Test
  void testGetWithBlankParametersShouldReturnError() {
    requestParams.put(REFERENCE_LIST, "   ");
    requestParams.put(PREFIX, "   ");
    requestParams.put(NAME_REFERENCE, "   ");
    requestParams.put("Help", "   ");
    requestParams.put(DESCRIPTION, "   ");

    service.get(requestParams, responseVars);

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals(ERR_MISSING_REFERENCE_LIST, responseVars.get(ERROR));
  }

  /**
   * A single item in ReferenceList results in exactly one List entity being saved.
   */
  @Test
  void testGetWithSingleItemShouldCreateOneReferenceListItem() {
    setupValidRequestParams();
    requestParams.put(REFERENCE_LIST, "SingleItem");
    setupSuccessfulCreationWithMessage();

    service.get(requestParams, responseVars);

    assertEquals(REFERENCE_CREATED_SUCCESS, responseVars.get(MESSAGE));
    verify(obDal, times(1)).save(any(List.class));
  }

  /**
   * Items with extra spaces are trimmed before being used as names.
   */
  @Test
  void testGetWithItemsWithSpacesShouldTrimItems() {
    setupValidRequestParams();
    requestParams.put(REFERENCE_LIST, " Item1 , Item2 , Item3 ");
    setupSuccessfulCreationWithMessage();

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
    setupValidRequestParams();
    requestParams.put(REFERENCE_LIST, "Active,Active Status,Activated");
    setupSuccessfulCreationWithMessage();

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
    setupValidRequestParams();
    requestParams.put(REFERENCE_LIST, "A,B,C");
    setupSuccessfulCreationWithMessage();

    service.get(requestParams, responseVars);

    assertEquals(REFERENCE_CREATED_SUCCESS, responseVars.get(MESSAGE));
    verify(obDal, times(3)).save(any(List.class));
  }

  /**
   * Ensures the Reference is initialized with expected properties before saving.
   */
  @Test
  void testGetCreatesReferenceWithCorrectProperties() {
    setupValidRequestParams();
    setupSuccessfulCreationWithMessage();

    service.get(requestParams, responseVars);

    verify(reference).setNewOBObject(true);
    verify(reference).setName(TEST_REFERENCE);
    verify(reference).setModule(module);
    verify(reference).setParentReference(parentReference);
    verify(reference).setHelpComment(TEST_HELP);
    verify(reference).setDescription(TEST_DESCRIPTION);
    verify(obDal).save(reference);
  }

  /**
   * Ensures each List item is initialized, linked to Reference and Module, and has name/searchKey.
   */
  @Test
  void testGetCreatesReferenceListItemsWithCorrectProperties() {
    setupValidRequestParams();
    setupSuccessfulCreationWithMessage();

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

    assertTrue(responseVars.containsKey(ERROR));
    assertEquals("Database error", responseVars.get(ERROR));
  }

  /**
   * A long ReferenceList should create and save one List entity per item.
   */
  @Test
  void testGetWithManyItemsShouldCreateAllItems() {
    setupValidRequestParams();
    requestParams.put(
        REFERENCE_LIST,
        "Item1,Item2,Item3,Item4,Item5,Item6,Item7,Item8,Item9,Item10"
    );
    setupSuccessfulCreationWithMessage();

    service.get(requestParams, responseVars);

    assertEquals(REFERENCE_CREATED_SUCCESS, responseVars.get(MESSAGE));
    verify(obDal, times(10)).save(any(List.class));
  }

  /**
   * Items containing special characters should be handled and persisted correctly.
   */
  @Test
  void testGetWithSpecialCharactersShouldHandleCorrectly() {
    setupValidRequestParams();
    requestParams.put(REFERENCE_LIST, "Item-1,Item_2,Item.3");
    setupSuccessfulCreationWithMessage();

    service.get(requestParams, responseVars);

    assertEquals(REFERENCE_CREATED_SUCCESS, responseVars.get(MESSAGE));
    verify(obDal, times(3)).save(any(List.class));
  }

  /**
   * Populates requestParams with a minimal valid payload shared across tests.
   */
  private void setupValidRequestParams() {
    requestParams.put(REFERENCE_LIST, REFERENCE_LIST_ITEMS);
    requestParams.put(PREFIX, "TEST");
    requestParams.put(NAME_REFERENCE, TEST_REFERENCE);
    requestParams.put("Help", TEST_HELP);
    requestParams.put(DESCRIPTION, TEST_DESCRIPTION);
  }

  /**
   * Populates requestParams with all mandatory parameters except the reference list.
   */
  private void setupBaseParamsWithoutReferenceList() {
    requestParams.put(PREFIX, "TEST");
    requestParams.put(NAME_REFERENCE, TEST_REFERENCE);
    requestParams.put("Help", TEST_HELP);
    requestParams.put(DESCRIPTION, TEST_DESCRIPTION);
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

  /**
   * Configures common mocks and a localized success message for the happy-path creation flow.
   */
  private void setupSuccessfulCreationWithMessage() {
    setupMocksForSuccessfulCreation();
    messageMock.when(() -> OBMessageUtils.messageBD(MSG_REFERENCE_CREATED))
        .thenReturn(REFERENCE_CREATED_SUCCESS);
  }
}
