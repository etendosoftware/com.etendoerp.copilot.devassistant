package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.module.ModuleDependency;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * The {@code CreateModuleWebHook} class is responsible for handling the creation of a new
 * module in the system via a webhook event. It extends {@link BaseWebhookService} and provides
 * functionality to register a module, define its dependencies, database prefix, and associated
 * Java package.
 *
 * The webhook expects parameters like module name, Java package, description, help comments,
 * version, license, and database prefix, and validates them before registering the module.
 *
 * The key steps in this webhook are:
 * <ul>
 *   <li>Validation of required parameters</li>
 *   <li>Module registration in the system</li>
 *   <li>Setting the module's dependencies</li>
 *   <li>Setting the database prefix and Java package</li>
 * </ul>
 *
 * If any errors occur during the registration process, the webhook catches and logs them,
 * and returns an appropriate error message in the response.
 */

public class CreateModuleWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  private static final String ERROR_PROPERTY = "error";

  private static final String PARAM_JAVA_PACKAGE = "JavaPackage";
  private static final String PARAM_MODULE_NAME = "ModuleName";
  private static final String PARAM_DESCRIPTION = "Description";
  private static final String PARAM_HELP_COMMENT = "HelpComment";
  private static final String PARAM_VERSION = "Version";
  private static final String PARAM_LICENSE = "ModuleLicense";
  private static final String PARAM_DBPREFIX = "DBPrefix";
  private static final String CORE_DEPENDENCY = "0";
  private static final String DEPENDENCY_ENFORCEMENT = "MAJOR";
  private static final String DATA_PACKAGE_SUFFIX = " Data Package";


  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {

    logExecutionInit(parameter, log);
    log.debug("Starting Module registration");

    try {
      validateParameters(parameter);
      createModuleDefinition(parameter);
      responseVars.put("message", OBMessageUtils.getI18NMessage("COPDEV_ModuleCreated"));
    } catch (IllegalArgumentException e) {
      log.error("Validation error: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error creating module", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }

  }

  private void validateParameters(Map<String, String> parameter) {

    String missingParameterMessage = "COPDEV_MissingParameter";

    if (isInvalidParameter(parameter.get(PARAM_JAVA_PACKAGE))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_JAVA_PACKAGE }));
    }

    if (isInvalidParameter(parameter.get(PARAM_MODULE_NAME))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_MODULE_NAME }));
    }

    if (isInvalidParameter(parameter.get(PARAM_VERSION))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_VERSION }));
    }

  }

  private boolean isInvalidParameter(String parameter) {
    return StringUtils.isBlank(parameter);
  }

  private String mapLicenseType(String license) {
    switch (license) {
      case "Apache License 2.0":
        return "Apache2.0";
      case "Openbravo Public License":
        return "OBPL";
      case "Mozilla Public License 1.1":
        return "MPL1.1";
      case "Etendo Commercial License":
        return "ETCL";
      default:
        throw new IllegalArgumentException("Invalid license type: " + license);
    }
  }

  private void createModuleDefinition(Map<String, String> parameter) {

    try {
      OBContext.setAdminMode(true);
      Module moduleDef = createModuleHeader(parameter);
      String dbPrefixValue = parameter.get(PARAM_DBPREFIX);
      createDependencyModule(CORE_DEPENDENCY, moduleDef);
      createDBPrefixModule(moduleDef, dbPrefixValue);
      createDataPackageModule(moduleDef, parameter.get(PARAM_JAVA_PACKAGE));
    } finally {
      OBContext.restorePreviousMode();
    }
  }


  private Module createModuleHeader(Map<String, String> parameter) {
    String license = mapLicenseType(parameter.get(PARAM_LICENSE));
    String javaPackage = parameter.get(PARAM_JAVA_PACKAGE);
    String moduleName = parameter.get(PARAM_MODULE_NAME);
    String description = parameter.get(PARAM_DESCRIPTION);
    String helpComment = parameter.get(PARAM_HELP_COMMENT);
    String version = parameter.get(PARAM_VERSION);

    Module moduleDef = OBProvider.getInstance().get(Module.class);
    moduleDef.setNewOBObject(true);
    moduleDef.setJavaPackage(javaPackage);
    moduleDef.setName(moduleName);
    moduleDef.setDescription(description);
    moduleDef.setHelpComment(helpComment);
    moduleDef.setVersion(version);
    moduleDef.setLicenseType(license);
    moduleDef.setActive(true);

    OBDal.getInstance().save(moduleDef);
    OBDal.getInstance().flush();
    return moduleDef;
  }

  private void createDependencyModule(String moduleId, Module moduleDef) {
    Module moduleDep = getModuleById(moduleId);

    if (moduleDep == null) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_MissingModule"));
    }

    ModuleDependency moduleDependency = OBProvider.getInstance().get(ModuleDependency.class);
    moduleDependency.setModule(moduleDef);
    moduleDependency.setDependentModule(moduleDep);
    moduleDependency.setFirstVersion(moduleDep.getVersion());
    moduleDependency.setDependencyEnforcement(DEPENDENCY_ENFORCEMENT);
    OBDal.getInstance().save(moduleDependency);
    OBDal.getInstance().flush();
  }

  private Module getModuleById(String moduleId) {
    return OBDal.getInstance().get(Module.class, moduleId);
  }

  private void createDBPrefixModule(Module moduleDef, String dbprefix) {

    ModuleDBPrefix moduleDBPrefix = OBProvider.getInstance().get(ModuleDBPrefix.class);
    moduleDBPrefix.setModule(moduleDef);
    moduleDBPrefix.setName(dbprefix);
    OBDal.getInstance().save(moduleDBPrefix);
    OBDal.getInstance().flush();

  }

  private void createDataPackageModule(Module moduleDef, String javaPackage) {
    DataPackage dataPackage = OBProvider.getInstance().get(DataPackage.class);

    dataPackage.setModule(moduleDef);
    dataPackage.setName(moduleDef.getName() + DATA_PACKAGE_SUFFIX);
    dataPackage.setJavaPackage(javaPackage + ".data");
    OBDal.getInstance().save(dataPackage);
    OBDal.getInstance().flush();

  }
}