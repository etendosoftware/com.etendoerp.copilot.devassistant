package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

public class ModuleInfoReader extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";
  private static final String MODULE_NAME_PARAM = "ModuleName";
  private static final String MODULE_PREFIX = "ModulePrefix";
  private static final String MODULE_JAVAPACKAGE = "ModulePackage";
  private static final String MODULE_ID = "ModuleID";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Executing WebHook: ModuleInfoReader");
    String moduleName = parameter.get(MODULE_NAME_PARAM);
    String modulePrefix = parameter.get(MODULE_PREFIX);
    String moduleJavapackage = parameter.get(MODULE_JAVAPACKAGE);
    String moduleID = parameter.get(MODULE_ID);

    if (moduleName == null && modulePrefix == null && moduleJavapackage == null && moduleID == null) {
      responseVars.put("error", "Missing parameters.");
      return;
    }
    Module module = null;
    if (StringUtils.isNotEmpty(moduleName)) {
      module = Utils.getModuleByName(moduleName);
    } else if (StringUtils.isNotEmpty(modulePrefix)) {
      module = Utils.getModuleByPrefix(modulePrefix);
    } else if (StringUtils.isNotEmpty(moduleJavapackage)) {
      module = Utils.getModuleByJavaPackage(moduleJavapackage);
    } else if (StringUtils.isNotEmpty(moduleID)) {
      module = Utils.getModuleByID(moduleID);
    }

    if (module != null) {
      String moduleInfo = String.format("Name: %s, Type: %s, ID: %s, Prefix: %s, Data Package: %s",
          module.getName(), module.getType(), module.getId(), module.getModuleDBPrefixList(), module.getDataPackageList());
      responseVars.put(MESSAGE, moduleInfo);
    } else {
      responseVars.put("error", "Module not found.");
    }
  }
}
