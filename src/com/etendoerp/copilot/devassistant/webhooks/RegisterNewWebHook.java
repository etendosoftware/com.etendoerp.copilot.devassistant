package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedWebhookParam;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;
import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterNewWebHook extends BaseWebhookService {


  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";
  private static final String ERROR = "error";

  /**
   * Handles the GET request for registering a new webhook.
   *
   * @param parameter
   *     a map containing the parameters for the webhook registration
   * @param responseVars
   *     a map to store the response variables
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

    if (StringUtils.isBlank(javaclass) || StringUtils.isBlank(searchkey) || StringUtils.isBlank(
        webhookParams) || StringUtils.isBlank(moduleJavaPackage)) {
      responseVars.put(ERROR,
          String.format(OBMessageUtils.messageBD("COPDEV_MisisngParameters")));
      return;
    }
    //dividing the webhookParams by semicolon
    String[] params = webhookParams.split(";");
    Module module = Utils.getModuleByJavaPackage(moduleJavaPackage);
    String moduleNotFoundMessage = "";
    if (module == null) {
      moduleNotFoundMessage = OBMessageUtils.messageBD("COPDEV_SetModuleManually");
    }

    DefinedWebHook webhookHeader = OBProvider.getInstance().get(DefinedWebHook.class);
    webhookHeader.setNewOBObject(true);
    webhookHeader.setModule(module);
    webhookHeader.setJavaClass(javaclass);
    webhookHeader.setName(searchkey);
    webhookHeader.setAllowGroupAccess(true);
    OBDal.getInstance().save(webhookHeader);
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
    DefinedwebhookRole whRole = OBProvider.getInstance().get(DefinedwebhookRole.class);
    whRole.setNewOBObject(true);
    whRole.setModuleID(module);
    whRole.setSmfwheDefinedwebhook(webhookHeader);
    whRole.setRole(OBContext.getOBContext().getRole());
    OBDal.getInstance().save(whRole);

    OBDal.getInstance().flush();
    responseVars.put(MESSAGE,
        String.format(OBMessageUtils.messageBD("COPDEV_WebhookCreated"), webhookHeader.getName(), moduleNotFoundMessage));


  }
}

