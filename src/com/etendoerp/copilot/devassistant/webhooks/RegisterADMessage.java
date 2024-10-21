package com.etendoerp.copilot.devassistant.webhooks;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.ui.Message;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Service to register an AD_Message in the Openbravo database.
 * This class extends BaseWebhookService and provides the implementation for the get method
 * to handle the registration of AD_Message based on the provided parameters.
 */
public class RegisterADMessage extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";
  private static final String ERROR = "error";

  /**
   * Processes the webhook request to register an AD_Message.
   * This method validates the input parameters, checks the module and its prefixes,
   * and creates a new AD_Message in the database if all validations pass.
   *
   * @param parameter
   *     The map containing the request parameters.
   * @param responseVars
   *     The map to store the response variables.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Executing WebHook: RegisterADMessage");

    String moduleJavaPackage = parameter.get("ModuleJavaPackage");
    String searchKey = parameter.get("SearchKey");
    String messageType = parameter.get("MessageType");
    String messageText = parameter.get("MessageText");

    if (StringUtils.isBlank(moduleJavaPackage) || StringUtils.isBlank(searchKey) ||
        StringUtils.isBlank(messageType) || StringUtils.isBlank(messageText)) {
      responseVars.put(ERROR, OBMessageUtils.messageBD("COPDEV_MISSINGPARAMETERS"));
      return;
    }

    var module = Utils.getModuleByJavaPackage(moduleJavaPackage);
    if (module == null) {
      responseVars.put(ERROR, OBMessageUtils.messageBD("COPDEV_ModuleNotFound"));
      return;
    }
    List<ModuleDBPrefix> prefixList = module.getModuleDBPrefixList();
    if (prefixList.isEmpty()) {
      responseVars.put(ERROR, String.format(OBMessageUtils.messageBD("COPDEV_ModulePrefixMissing"), moduleJavaPackage));
      return;
    }

    boolean prefixCheck = false;
    for (ModuleDBPrefix prefix : prefixList) {
      if (StringUtils.startsWith(searchKey, prefix.getName() + "_")) {
        prefixCheck = true;
        break;
      }
    }
    if (!prefixCheck) {
      responseVars.put(ERROR,
          String.format(
              OBMessageUtils.messageBD("COPDEV_SearchKeyPrefixMismatch"),
              prefixList.stream().map(ModuleDBPrefix::getName).reduce((a, b) -> a + ", " + b).orElse("")
          )
      );
      return;
    }

    try {
      OBContext.setAdminMode();
      Message adMessage = OBProvider.getInstance().get(Message.class);
      adMessage.setNewOBObject(true);
      adMessage.setSearchKey(searchKey);
      adMessage.setMessageText(messageText);
      adMessage.setMessageType(messageType);
      adMessage.setModule(module);
      OBDal.getInstance().save(adMessage);
      OBDal.getInstance().flush();

      responseVars.put(MESSAGE,
          String.format(
              OBMessageUtils.messageBD("COPDEV_ADMessageSuccess"),
              searchKey
          )
      );
    } catch (Exception e) {
      LOG.error("Error creating AD_Message", e);
      responseVars.put(ERROR, String.format(OBMessageUtils.messageBD("COPDEV_ADMessageCreationError"), e.getMessage()));
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}