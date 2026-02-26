package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.etendorx.data.OpenAPITab;
import com.etendoerp.openapi.data.OpenAPIRequest;
import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to register a headless EtendoRX endpoint atomically.
 * Creates an {@code ETAPI_OpenAPI_Req} (OpenAPIRequest) and links it to an
 * {@code ETRX_OpenAPI_Tab} pointing to the specified AD_Tab.
 *
 * <p>Required parameters:
 * <ul>
 *   <li>{@code RequestName} — endpoint URL segment (e.g. "MyProducts").
 *       Accessible at {@code /sws/com.etendoerp.etendorx.datasource/MyProducts}</li>
 *   <li>{@code ModuleID} — AD_MODULE_ID of the module that owns this endpoint</li>
 *   <li>{@code TabID} — AD_TAB_ID to expose, OR</li>
 *   <li>{@code TableName} — DB table name to auto-resolve the first tab (used when TabID absent)</li>
 * </ul>
 *
 * <p>Optional parameters:
 * <ul>
 *   <li>{@code Description} — endpoint description</li>
 *   <li>{@code Type} — endpoint type (defaults to "R" for REST)</li>
 * </ul>
 *
 * <p>Response: {@code {"message": "Headless endpoint registered: <RequestName> (ID: <req_id>)"}}
 */
public class RegisterHeadlessEndpoint extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String DEFAULT_TYPE = "R";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);

    String requestName = parameter.get("RequestName");
    String moduleId = parameter.get("ModuleID");
    String tabId = parameter.get("TabID");
    String tableName = parameter.get("TableName");
    String description = StringUtils.defaultIfEmpty(parameter.get("Description"), requestName);
    String type = StringUtils.defaultIfEmpty(parameter.get("Type"), DEFAULT_TYPE);

    try {
      validateRequiredParameters(requestName, moduleId, tabId, tableName);

      Module module = Utils.getModuleByID(moduleId);
      Tab tab = resolveTab(tabId, tableName);

      checkEndpointNotExists(requestName);

      OBContext.setAdminMode(true);
      try {
        OpenAPIRequest request = createOpenAPIRequest(requestName, description, type, module);
        createOpenAPITab(request, tab, module);
        OBDal.getInstance().flush();

        responseVars.put("message",
            String.format("Headless endpoint registered: %s (ID: %s). " +
                    "URL: /sws/com.etendoerp.etendorx.datasource/%s",
                requestName, request.getId(), requestName));
      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (Exception e) {
      LOG.error("Error registering headless endpoint: {}", e.getMessage(), e);
      responseVars.put("error", e.getMessage());
      OBDal.getInstance().getSession().clear();
    }
  }

  private void validateRequiredParameters(String requestName, String moduleId,
      String tabId, String tableName) {
    if (StringUtils.isBlank(requestName)) {
      throw new OBException("RequestName parameter is required");
    }
    if (StringUtils.isBlank(moduleId)) {
      throw new OBException("ModuleID parameter is required");
    }
    if (StringUtils.isBlank(tabId) && StringUtils.isBlank(tableName)) {
      throw new OBException("Either TabID or TableName parameter is required");
    }
  }

  private Tab resolveTab(String tabId, String tableName) {
    if (StringUtils.isNotBlank(tabId)) {
      Tab tab = OBDal.getInstance().get(Tab.class, tabId);
      if (tab == null) {
        throw new OBException(String.format("Tab with ID '%s' not found", tabId));
      }
      return tab;
    }

    // Resolve by table name: get the first header tab (TabLevel=0) for the table
    Table table = Utils.getTableByDBName(tableName);

    OBCriteria<Tab> tabCrit = OBDal.getInstance().createCriteria(Tab.class);
    tabCrit.add(Restrictions.eq(Tab.PROPERTY_TABLE, table));
    tabCrit.add(Restrictions.eq(Tab.PROPERTY_TABLEVEL, 0L));
    tabCrit.addOrderBy(Tab.PROPERTY_SEQUENCENUMBER, true);
    tabCrit.setMaxResults(1);

    Tab tab = (Tab) tabCrit.uniqueResult();
    if (tab == null) {
      // Fallback: any tab for this table
      OBCriteria<Tab> fallbackCrit = OBDal.getInstance().createCriteria(Tab.class);
      fallbackCrit.add(Restrictions.eq(Tab.PROPERTY_TABLE, table));
      fallbackCrit.addOrderBy(Tab.PROPERTY_SEQUENCENUMBER, true);
      fallbackCrit.setMaxResults(1);
      tab = (Tab) fallbackCrit.uniqueResult();
    }

    if (tab == null) {
      throw new OBException(
          String.format("No tab found for table '%s'. Create a window/tab first.", tableName));
    }
    return tab;
  }

  private void checkEndpointNotExists(String requestName) {
    OBCriteria<OpenAPIRequest> crit = OBDal.getInstance().createCriteria(OpenAPIRequest.class);
    crit.add(Restrictions.eq(OpenAPIRequest.PROPERTY_NAME, requestName));
    crit.setMaxResults(1);
    if (crit.uniqueResult() != null) {
      throw new OBException(
          String.format("Headless endpoint '%s' already exists", requestName));
    }
  }

  private OpenAPIRequest createOpenAPIRequest(String name, String description,
      String type, Module module) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    OpenAPIRequest request = OBProvider.getInstance().get(OpenAPIRequest.class);
    request.setNewOBObject(true);
    request.setClient(client);
    request.setOrganization(org);
    request.setActive(true);
    request.setCreationDate(new Date());
    request.setCreatedBy(OBContext.getOBContext().getUser());
    request.setUpdated(new Date());
    request.setUpdatedBy(OBContext.getOBContext().getUser());
    request.setName(name);
    request.setDescription(description);
    request.setType(type);
    request.setModule(module);

    OBDal.getInstance().save(request);
    LOG.info("OpenAPIRequest '{}' created with ID: {}", name, request.getId());
    return request;
  }

  private void createOpenAPITab(OpenAPIRequest request, Tab tab, Module module) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    OpenAPITab openAPITab = OBProvider.getInstance().get(OpenAPITab.class);
    openAPITab.setNewOBObject(true);
    openAPITab.setClient(client);
    openAPITab.setOrganization(org);
    openAPITab.setActive(true);
    openAPITab.setCreationDate(new Date());
    openAPITab.setCreatedBy(OBContext.getOBContext().getUser());
    openAPITab.setUpdated(new Date());
    openAPITab.setUpdatedBy(OBContext.getOBContext().getUser());
    openAPITab.setOpenAPIRequest(request);
    openAPITab.setRelatedTabs(tab);
    openAPITab.setModule(module);

    OBDal.getInstance().save(openAPITab);
    LOG.info("OpenAPITab created linking request '{}' to tab '{}'",
        request.getName(), tab.getName());
  }
}
