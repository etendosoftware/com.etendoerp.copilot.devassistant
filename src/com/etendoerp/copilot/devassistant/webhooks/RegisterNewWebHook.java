package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedWebhookParam;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Class that handles the registration of a new webhook. This class extends BaseWebhookService and
 * defines the logic to process a GET request for webhook registration, including validation of input
 * parameters, module retrieval, and storing webhook-related objects in the database.
 */
public class RegisterNewWebHook extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";
  private static final String ERROR = "error";

  /**
   * Processes a GET request to register a new webhook.
   *
   * This method takes parameters such as the Java class name, search key, webhook parameters, and
   * module Java package, validates them, and then registers the webhook in the database, along with
   * the related parameters and roles.
   *
   * @param parameter a map containing the parameters required for the webhook registration:
   *                  - "Javaclass": The Java class name of the webhook.
   *                  - "SearchKey": The search key (name) of the webhook.
   *                  - "Params": The parameters of the webhook, separated by semicolons.
   *                  - "ModuleJavaPackage": The Java package of the module where the webhook belongs.
   * @param responseVars a map to store the response variables, such as success or error messages.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Executing WebHook: RegisterNewWebHook");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.debug("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String javaclass = parameter.get("Javaclass");
    String searchkey = parameter.get("SearchKey");
    String webhookParams = parameter.get("Params");
    String moduleJavaPackage = parameter.get("ModuleJavaPackage");

    // Validate mandatory parameters
    if (StringUtils.isBlank(javaclass) || StringUtils.isBlank(searchkey) || StringUtils.isBlank(
        webhookParams) || StringUtils.isBlank(moduleJavaPackage)) {
      responseVars.put(ERROR,
          String.format(OBMessageUtils.messageBD("COPDEV_MisisngParameters")));
      return;
    }

    // Split webhookParams by semicolon
    String[] params = webhookParams.split(";");
    Module module = Utils.getModuleByJavaPackage(moduleJavaPackage);
    String moduleNotFoundMessage = "";
    if (module == null) {
      moduleNotFoundMessage = OBMessageUtils.messageBD("COPDEV_SetModuleManually");
    }

    // Create and register the webhook header
    DefinedWebHook webhookHeader = OBProvider.getInstance().get(DefinedWebHook.class);
    webhookHeader.setNewOBObject(true);
    webhookHeader.setModule(module);
    webhookHeader.setJavaClass(javaclass);
    webhookHeader.setName(searchkey);
    webhookHeader.setAllowGroupAccess(true);
    OBDal.getInstance().save(webhookHeader);

    // Register each webhook parameter
    for (String param : params) {
      String paramName = StringUtils.trim(param);
      if (StringUtils.isNotBlank(paramName)) {
        DefinedWebhookParam whParameter = OBProvider.getInstance().get(DefinedWebhookParam.class);
        whParameter.setNewOBObject(true);
        whParameter.setModule(module);
        whParameter.setSmfwheDefinedwebhook(webhookHeader);
        whParameter.setName(paramName);
        whParameter.setRequired(true);
        OBDal.getInstance().save(whParameter);
      }
    }

    // Assign the role to the webhook
    DefinedwebhookRole whRole = OBProvider.getInstance().get(DefinedwebhookRole.class);
    whRole.setNewOBObject(true);
    whRole.setModuleID(module);
    whRole.setSmfwheDefinedwebhook(webhookHeader);
    whRole.setRole(OBContext.getOBContext().getRole());
    OBDal.getInstance().save(whRole);

    // Commit all changes to the database
    OBDal.getInstance().flush();

    // Return success message
    responseVars.put(MESSAGE,
        String.format(OBMessageUtils.messageBD("COPDEV_WebhookCreated"), webhookHeader.getName(), moduleNotFoundMessage));
  }
}
