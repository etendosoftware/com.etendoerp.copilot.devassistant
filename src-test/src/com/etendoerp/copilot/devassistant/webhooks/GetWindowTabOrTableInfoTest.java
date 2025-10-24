package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.test.base.TestConstants;

/**
 * Tests for {@link GetWindowTabOrTableInfo} webhook service.
 */
public class GetWindowTabOrTableInfoTest extends WeldBaseTest {
  // Parameter keys
  private static final String PARAM_NAME = "Name";
  private static final String PARAM_KEYWORD = "KeyWord";

  // Response keys
  private static final String ERROR_KEY = "error";
  private static final String QUERY_EXECUTED_KEY = "QueryExecuted";
  private static final String COLUMNS_KEY = "Columns";
  private static final String DATA_KEY = "Data";

  // Test data
  private static final String EXISTING_TABLE_ID = "6344EB0DE29E4E52ACF99F591FFCD07D"; // ETCOP_App table

  /**
   * Initializes DAL and request context before each test.
   */
  @Before
  public void setUpContext() throws Exception {
    super.setUp();
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);
  }

  /**
   * Verifies that omitting the KeyWord parameter produces an error response.
   */
  @Test
  public void missingKeyWordProducesError() {
    GetWindowTabOrTableInfo svc = new GetWindowTabOrTableInfo();
    Map<String, String> params = new HashMap<>();
    params.put(PARAM_NAME, "anything");
    Map<String, String> resp = new HashMap<>();
    svc.get(params, resp);
    assertTrue(resp.containsKey(ERROR_KEY));
    assertTrue(resp.get(ERROR_KEY).toLowerCase().contains("keyword"));
  }

  /**
   * Verifies that an invalid KeyWord value returns an error.
   */
  @Test
  public void invalidKeyWordProducesError() {
    GetWindowTabOrTableInfo svc = new GetWindowTabOrTableInfo();
    Map<String, String> params = new HashMap<>();
    params.put(PARAM_NAME, "whatever");
    params.put(PARAM_KEYWORD, "invalidType");
    Map<String, String> resp = new HashMap<>();
    svc.get(params, resp);
    assertTrue(resp.containsKey(ERROR_KEY));
    assertTrue(resp.get(ERROR_KEY).toLowerCase().contains("key word is not correct"));
  }

  /**
   * Ensures a valid 'table' query returns expected column metadata and at least one data row.
   * Uses an existing table ID for guaranteed match.
   */
  @Test
  public void validTableKeywordReturnsData() throws JSONException {
    GetWindowTabOrTableInfo svc = new GetWindowTabOrTableInfo();
    Map<String, String> params = new HashMap<>();
    // Use the ID so the equality part of the WHERE clause matches even if name filtering fails.
    params.put(PARAM_NAME, EXISTING_TABLE_ID);
    params.put(PARAM_KEYWORD, "table");
    Map<String, String> resp = new HashMap<>();
    svc.get(params, resp);

    assertFalse("Should not return error for valid table keyword", resp.containsKey(ERROR_KEY));
    assertTrue(resp.containsKey(QUERY_EXECUTED_KEY));
    assertTrue(resp.containsKey(COLUMNS_KEY));
    assertTrue(resp.containsKey(DATA_KEY));

    JSONArray cols = new JSONArray(resp.get(COLUMNS_KEY));
    // Expect 3 columns: ad_table_id, tablename, name
    assertEquals(3, cols.length());
    // Basic name checks (case-insensitive)
    String first = cols.getString(0).toLowerCase();
    assertTrue(first.contains("ad_table_id"));

    JSONArray data = new JSONArray(resp.get(DATA_KEY));
    assertTrue("Expected at least one matching row", data.length() > 0);
    // Each row should have same number of elements as columns
    JSONArray row0 = data.getJSONArray(0);
    assertEquals(cols.length(), row0.length());
  }

  /**
   * Ensures a valid 'column' query returns column metadata including parent table id and data rows.
   * Uses an existing column ID from the known table.
   */
  @Test
  public void validColumnKeywordReturnsData() throws JSONException {
    // Obtain one existing column id from a known table to guarantee a match
    Table table = OBDal.getInstance().get(Table.class, EXISTING_TABLE_ID);
    assertNotNull("Precondition: table must exist", table);
    List<Column> colList = table.getADColumnList();
    assertFalse("Precondition: table must have columns", colList.isEmpty());
    String existingColumnId = colList.get(0).getId();

    GetWindowTabOrTableInfo svc = new GetWindowTabOrTableInfo();
    Map<String, String> params = new HashMap<>();
    params.put(PARAM_NAME, existingColumnId); // direct ID match
    params.put(PARAM_KEYWORD, "column");
    Map<String, String> resp = new HashMap<>();
    svc.get(params, resp);

    assertFalse(resp.containsKey(ERROR_KEY));
    assertTrue(resp.containsKey(QUERY_EXECUTED_KEY));
    assertTrue(resp.containsKey(COLUMNS_KEY));
    assertTrue(resp.containsKey(DATA_KEY));

    JSONArray cols = new JSONArray(resp.get(COLUMNS_KEY));
    // Expect 3 columns: ad_column_id, ad_table_id, name
    assertEquals(3, cols.length());
    String first = cols.getString(0).toLowerCase();
    assertTrue(first.contains("ad_column_id"));
    String second = cols.getString(1).toLowerCase();
    assertTrue(second.contains("ad_table_id"));

    JSONArray data = new JSONArray(resp.get(DATA_KEY));
    assertTrue(data.length() > 0);
    JSONArray row0 = data.getJSONArray(0);
    assertEquals(cols.length(), row0.length());

    // Optional: verify the first column value equals provided ID
    String returnedId = row0.getString(0);
    assertEquals(existingColumnId, returnedId);
  }

  /**
   * Extra sanity: querying a tab by id returns data (if any tab from window exists); if no tab found, still no error.
   */
  @Test
  public void validTabQueryHandlesNoResultsGracefully() throws JSONException {
    // Find any existing tab id by querying for one linked to the known table (via its tabs) if possible
    // Fallback to a non-existent id to ensure graceful empty result.
    String tabId = findAnyTabIdForTable(EXISTING_TABLE_ID);
    if (tabId == null) {
      tabId = "00000000000000000000000000000000"; // improbable id
    }

    GetWindowTabOrTableInfo svc = new GetWindowTabOrTableInfo();
    Map<String, String> params = new HashMap<>();
    params.put(PARAM_NAME, tabId);
    params.put(PARAM_KEYWORD, "tab");
    Map<String, String> resp = new HashMap<>();
    svc.get(params, resp);

    // Even if no data, should not be an error if keyword valid
    assertFalse(resp.containsKey(ERROR_KEY));
    assertTrue(resp.containsKey(QUERY_EXECUTED_KEY));
    assertTrue(resp.containsKey(COLUMNS_KEY));
    assertTrue(resp.containsKey(DATA_KEY));

    JSONArray cols = new JSONArray(resp.get(COLUMNS_KEY));
    // Expect 3 columns: ad_tab_id, ad_window_id, name
    assertEquals(3, cols.length());
    assertTrue(cols.getString(0).toLowerCase().contains("ad_tab_id"));
    assertTrue(cols.getString(1).toLowerCase().contains("ad_window_id"));

    // Data may be empty; ensure structure is valid JSON array
    new JSONArray(resp.get(DATA_KEY));
  }

  /**
   * Attempts to find any Tab ID related to a given table by joining through columns & fields if possible.
   * Returns null if none found quickly.
   */
  private String findAnyTabIdForTable(String tableId) {
    try {
      // Query AD_Tab where AD_Table_ID matches or related through column existence.
      String hql = "from org.openbravo.model.ad.ui.Tab t where t.table.id = :tableId";
      @SuppressWarnings("unchecked")
      List<org.openbravo.model.ad.ui.Tab> tabs = (List<org.openbravo.model.ad.ui.Tab>) OBDal.getInstance().getSession()
          .createQuery(hql).setParameter("tableId", tableId).setMaxResults(1).list();
      if (!tabs.isEmpty()) {
        return tabs.get(0).getId();
      }
    } catch (Exception ignored) {
      // Ignore and fallback to null
    }
    // Alternate attempt using criteria
    try {
      OBCriteria<org.openbravo.model.ad.ui.Tab> crit = OBDal.getInstance().createCriteria(org.openbravo.model.ad.ui.Tab.class);
      crit.add(Restrictions.eq("table.id", tableId));
      crit.setMaxResults(1);
      List<org.openbravo.model.ad.ui.Tab> list = crit.list();
      if (!list.isEmpty()) {
        return list.get(0).getId();
      }
    } catch (Exception ignored) {
      // Ignore and fallback to null
    }
    return null;
  }
}
