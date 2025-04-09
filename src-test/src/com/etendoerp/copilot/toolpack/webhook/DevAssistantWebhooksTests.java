package com.etendoerp.copilot.toolpack.webhook;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.copilot.devassistant.webhooks.CreateColumn;

/**
 * Unit tests for the Webhooks in the Copilot Toolpack.
 */

public class DevAssistantWebhooksTests extends WeldBaseTest {
  public static final String DATA = "data";
  public static final String ID = "id";
  public static final String REFERENCE_ID_TEXT = "14";
  private static final String C_ORDER_TABLE_ID = "259";
  private AutoCloseable mocks;

  /**
   * Sets up the test environment before each test.
   *
   * @throws Exception
   *     if an error occurs during setup
   */
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    super.setUp();

    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);

    OBDal.getInstance().flush();

    vars.setSessionValue("#User_Client", OBContext.getOBContext().getCurrentClient().getId());
    RequestContext.get().setVariableSecureApp(vars);
    OBPropertiesProvider.setInstance(new OBPropertiesProvider());
  }


  /**
   * Tests the simSearch method.
   * <p>
   * This test method verifies the functionality of the simSearch method in the SimSearch class.
   * It tests the search functionality and checks the responses.
   * </p>
   *
   * @throws Exception
   *     if an error occurs during the test
   */
  @Test
  public void CreateColumnTest() throws Exception {
    Map<String, String> parameter = null;

    var ccw = new CreateColumn();
    Map<String, String> respVars;

    // CASE 1: String column
    parameter = new HashMap<>();
    parameter.put("canBeNull", "false");
    parameter.put("columnNameDB", "mytext");
    parameter.put("defaultValue", "'hello'");
    parameter.put("moduleID", "77E11BDECDEB44008DD2235D259A77D7");
    parameter.put("name", "My Text Test");
    parameter.put("referenceID", REFERENCE_ID_TEXT); //
    parameter.put("tableID", C_ORDER_TABLE_ID);


    respVars = new HashMap<>();

    ccw.get(parameter, respVars);


    assertTrue(!respVars.keySet().isEmpty());
    assertTrue(StringUtils.isNotEmpty(respVars.get("message")));
    JSONObject json = new JSONObject(respVars.get("message"));
    assertTrue(json.has("data"));
    assertTrue(json.getJSONArray("data").length() > 0);
  }

  /**
   * Cleans up the test environment after each test.
   */
  @After
  public void tearDown() {
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vars = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId());
    RequestContext.get().setVariableSecureApp(vars);
    OBDal.getInstance().flush();
    OBDal.getInstance().commitAndClose();
  }
}
