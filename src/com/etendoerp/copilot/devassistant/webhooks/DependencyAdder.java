package com.etendoerp.copilot.devassistant.webhooks;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDependency;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * A service for adding dependencies between modules in the EtendoERP system. This class extends
 * {@link BaseWebhookService} and handles webhook calls to add dependencies between modules
 * based on their Java package names.
 */
public class DependencyAdder extends BaseWebhookService {

  private static final String ERROR_PROPERTY = "error";
  private static final Logger log = LogManager.getLogger(DependencyAdder.class);
  private static final String DEPENDENT_MODULE_JAVAPACKAGE = "DependentModuleJavaPackage";
  private static final String DEPENDENCY_MODULE_JAVAPACKAGE = "DependencyModuleJavaPackage";
  private static final String DEPENDENCY_ENFORCEMENT = "MAJOR";
  private static final String MISSING_PARAMETER = "COPDEV_MissingParameter";

  /**
   * Processes a webhook call to add a dependency between modules.
   * Retrieves the dependent and dependency modules by their Java package names, validates
   * the input, and creates a new dependency relationship if one does not already exist.
   *
   * @param parameter
   *     the input parameters for the webhook, containing the Java package names
   *     of the dependent and dependency modules.
   * @param responseVars
   *     the output variables for the webhook, used to return success or error messages.
   * @throws IllegalArgumentException
   *     if required parameters are missing or invalid.
   * @throws OBException
   *     if a module is not found or the dependency already exists.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.debug("Executing WebHook: DependencyAdder");
    String dependentModuleJavaPackage = parameter.get(DEPENDENT_MODULE_JAVAPACKAGE);
    String dependencyModuleJavaPackage = parameter.get(DEPENDENCY_MODULE_JAVAPACKAGE);

    if (StringUtils.isBlank(parameter.get(DEPENDENT_MODULE_JAVAPACKAGE))) {
      throw new OBException(String.format(OBMessageUtils.messageBD(MISSING_PARAMETER), DEPENDENT_MODULE_JAVAPACKAGE));
    }
    if (StringUtils.isBlank(parameter.get(DEPENDENCY_MODULE_JAVAPACKAGE))) {
      throw new OBException(String.format(OBMessageUtils.messageBD(MISSING_PARAMETER), DEPENDENCY_MODULE_JAVAPACKAGE));
    }

    Module dependentModule = Utils.getModuleByJavaPackage(dependentModuleJavaPackage);
    if (dependentModule == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_ModuleNotFound"));
    }
    Module dependencyModule = Utils.getModuleByJavaPackage(dependencyModuleJavaPackage);
    if (dependencyModule == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_ModuleNotFound"));
    }

    try {
      ModuleDependency moduleDependency = OBProvider.getInstance().get(ModuleDependency.class);
      moduleDependency.setModule(dependentModule);

      if (isDependencyAlreadyAdded(moduleDependency, dependencyModule)) {
        throw new OBException(OBMessageUtils.messageBD("COPDEV_DependencyAlreadyAdded"));
      }

      moduleDependency.setDependentModule(dependencyModule);
      moduleDependency.setFirstVersion(dependencyModule.getVersion());
      moduleDependency.setDependencyEnforcement(DEPENDENCY_ENFORCEMENT);
      OBDal.getInstance().save(moduleDependency);
      OBDal.getInstance().flush();

      responseVars.put("message",
          (String.format(OBMessageUtils.messageBD("COPDEV_DependencyAdded"), dependencyModule.getName(),
              dependentModule.getName())));
    } catch (IllegalArgumentException e) {
      log.error("Validation error: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error adding dependency", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  /**
   * Checks if a dependency between the given module and its dependent module
   * already exists.
   *
   * @param moduleDependency
   *     the {@link ModuleDependency} object containing the dependent module.
   * @param dependencyModule
   *     the module to check as a dependency.
   * @return true if the dependency already exists, false otherwise.
   */
  private boolean isDependencyAlreadyAdded(ModuleDependency moduleDependency, Module dependencyModule) {
    List<ModuleDependency> dependencyList = moduleDependency.getModule().getModuleDependencyList();
    for (ModuleDependency dependency : dependencyList) {
      if (StringUtils.equals(dependency.getDependantModuleName(), dependencyModule.getName())) {
        return true;
      }
    }
    return false;
  }

}
