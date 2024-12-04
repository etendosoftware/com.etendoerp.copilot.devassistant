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

public class DependencyAdder extends BaseWebhookService {

  private static final String ERROR_PROPERTY = "error";
  private static final Logger log = LogManager.getLogger();
  private static final String DEPENDENT_MODULE_JAVAPACKAGE = "DependentModuleJavaPackage";
  private static final String DEPENDENCY_MODULE_JAVAPACKAGE = "DependencyModuleJavaPackage";
  private static final String DEPENDENCY_ENFORCEMENT = "MAJOR";
  private static final String MISSING_PARAMETER = "COPDEV_MissingParameter";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.debug("Executing WebHook: DependencyAdder");
    String dependentModuleJavaPackage = parameter.get(DEPENDENT_MODULE_JAVAPACKAGE);
    String dependencyModuleJavaPackage = parameter.get(DEPENDENCY_MODULE_JAVAPACKAGE);

    if (Utils.isInvalidParameter(parameter.get(DEPENDENT_MODULE_JAVAPACKAGE))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(MISSING_PARAMETER, new String[]{ DEPENDENT_MODULE_JAVAPACKAGE }));
    }
    if (Utils.isInvalidParameter(parameter.get(DEPENDENCY_MODULE_JAVAPACKAGE))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(MISSING_PARAMETER, new String[]{ DEPENDENCY_MODULE_JAVAPACKAGE }));
    }

    Module dependentModule = Utils.getModuleByJavaPackage(dependentModuleJavaPackage);
    Module dependencyModule = Utils.getModuleByJavaPackage(dependencyModuleJavaPackage);

    try {
      ModuleDependency moduleDependency = OBProvider.getInstance().get(ModuleDependency.class);
      moduleDependency.setModule(dependentModule);

      List<ModuleDependency> dependencyList = moduleDependency.getModule().getModuleDependencyList();
      for (ModuleDependency dependency : dependencyList) {
        if (StringUtils.equals(dependency.getDependantModuleName(), dependencyModule.getName())) {
          throw new OBException(OBMessageUtils.messageBD("COPDEV_DependentAlreadyAdded"));
        }
      }

      moduleDependency.setDependentModule(dependencyModule);
      moduleDependency.setFirstVersion(dependencyModule.getVersion());
      moduleDependency.setDependencyEnforcement(DEPENDENCY_ENFORCEMENT);
      OBDal.getInstance().save(moduleDependency);
      OBDal.getInstance().flush();

      responseVars.put("message", (String.format("The dependency of the %s was added to the %s.", dependencyModule.getName(), dependentModule.getName())));
    } catch (IllegalArgumentException e) {
      log.error("Validation error: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error adding dependency", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

}
