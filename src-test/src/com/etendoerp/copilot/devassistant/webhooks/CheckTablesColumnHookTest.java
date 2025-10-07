package com.etendoerp.copilot.devassistant.webhooks;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.test.base.TestConstants;
import org.openbravo.client.kernel.RequestContext;

/**
 * Tests for {@link CheckTablesColumnHook}.
 */
public class CheckTablesColumnHookTest extends WeldBaseTest {
  private static final String TABLE_ID = "TableID";
  private static final String MESSAGE = "message";
  private static final String EXISTING_TABLE_ID = "6344EB0DE29E4E52ACF99F591FFCD07D";

  @Before
  public void initContext() throws Exception {
    super.setUp();
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);
  }

  @Test
  public void missingTableIdProducesError() {
    CheckTablesColumnHook hook = new CheckTablesColumnHook();
    Map<String, String> params = new HashMap<>();
    Map<String, String> resp = new HashMap<>();
    hook.get(params, resp);
    assertTrue("Expected error key", resp.containsKey(CheckTablesColumnHook.ERROR));
    assertTrue(resp.get(CheckTablesColumnHook.ERROR).contains("No table ID"));
  }

  @Test
  public void invalidTableIdProducesError() {
    CheckTablesColumnHook hook = new CheckTablesColumnHook();
    Map<String, String> params = new HashMap<>();
    params.put(TABLE_ID, "NON_EXISTENT_ID");
    Map<String, String> resp = new HashMap<>();
    hook.get(params, resp);
    assertTrue(resp.containsKey(CheckTablesColumnHook.ERROR));
    assertTrue(resp.get(CheckTablesColumnHook.ERROR).contains("not found"));
  }

  @Test
  public void validTableWithModuleFiltersColumns() throws Exception {
    // Obtain table and one of its columns to get the module ID for filtering
    Table table = OBDal.getInstance().get(Table.class, EXISTING_TABLE_ID);
    assertNotNull("Precondition: table must exist", table);
    List<Column> cols = table.getADColumnList();
    assertFalse("Table must have columns", cols.isEmpty());
    String moduleId = cols.get(0).getModule().getId();

    CheckTablesColumnHook hook = new CheckTablesColumnHook();
    Map<String, String> params = new HashMap<>();
    params.put(TABLE_ID, EXISTING_TABLE_ID);
    params.put("ModuleID", moduleId);
    Map<String, String> resp = new HashMap<>();
    hook.get(params, resp);

    assertFalse("Should not return error for valid table", resp.containsKey(CheckTablesColumnHook.ERROR));
    assertTrue(resp.containsKey(MESSAGE));

    // Parse the message (JSON array of errors). It can be empty or contain objects.
    String raw = resp.get(MESSAGE);
    JSONArray arr = new JSONArray(raw);
    // Basic sanity: all entries (if any) must contain an 'error' key or be empty objects
    for (int i = 0; i < arr.length(); i++) {
      JSONObject obj = arr.getJSONObject(i);
      assertTrue(obj.has(CheckTablesColumnHook.ERROR));
      assertTrue(StringUtils.isNotBlank(obj.getString(CheckTablesColumnHook.ERROR)));
    }
  }

  @Test
  public void validTableWithNoMatchingModuleYieldsEmptyArray() throws Exception {
    // Fetch table
    Table table = OBDal.getInstance().get(Table.class, EXISTING_TABLE_ID);
    assertNotNull(table);

    // Pick a module id that is very unlikely to match (searching none)
    String impossibleModuleId = "00000000000000000000000000000001"; // 32 chars random-like

    // Ensure no column uses this module id
    OBCriteria<Column> criteria = OBDal.getInstance().createCriteria(Column.class);
    criteria.add(Restrictions.eq(Column.PROPERTY_TABLE, table));
    criteria.add(Restrictions.sqlRestriction("ad_module_id = ?", impossibleModuleId, org.hibernate.type.StringType.INSTANCE));
    assertTrue("Precondition: No columns should match fake module", criteria.list().isEmpty());

    CheckTablesColumnHook hook = new CheckTablesColumnHook();
    Map<String, String> params = new HashMap<>();
    params.put(TABLE_ID, EXISTING_TABLE_ID);
    params.put("ModuleID", impossibleModuleId);
    Map<String, String> resp = new HashMap<>();
    hook.get(params, resp);

    assertFalse(resp.containsKey(CheckTablesColumnHook.ERROR));
    assertTrue(resp.containsKey(MESSAGE));
    JSONArray arr = new JSONArray(resp.get(MESSAGE));
    assertEquals("Expect no validated columns when module filter excludes all", 0, arr.length());
  }
}
