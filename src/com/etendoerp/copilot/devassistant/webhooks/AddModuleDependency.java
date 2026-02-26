package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Date;
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
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDependency;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to add a dependency between two modules (AD_MODULE_DEPENDENCY).
 * Used to declare that a module requires another module, or to include a module in a template.
 *
 * <p>Required parameters:
 * <ul>
 *   <li>{@code ModuleID} — AD_MODULE_ID of the module that depends on another</li>
 *   <li>{@code DependsOnModuleID} — AD_MODULE_ID of the required module (or use {@code DependsOnJavaPackage})</li>
 *   <li>{@code DependsOnJavaPackage} — java package of the required module (alternative to DependsOnModuleID)</li>
 * </ul>
 *
 * <p>Optional parameters:
 * <ul>
 *   <li>{@code FirstVersion} — minimum required version, e.g. {@code "3.0.0"}</li>
 *   <li>{@code LastVersion} — maximum compatible version</li>
 *   <li>{@code IsIncluded} — {@code "true"} for template "included modules" (default false)</li>
 *   <li>{@code Enforcement} — {@code "MAJOR"}, {@code "MINOR"}, {@code "NONE"} (default MAJOR)</li>
 * </ul>
 *
 * <p>Response: {@code {"message": "Dependency added: <moduleJavaPackage> depends on <dependsOnJavaPackage>"}}
 */
public class AddModuleDependency extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String DEFAULT_ENFORCEMENT = "MAJOR";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);

    String moduleId = parameter.get("ModuleID");
    String dependsOnModuleId = parameter.get("DependsOnModuleID");
    String dependsOnJavaPackage = parameter.get("DependsOnJavaPackage");
    String firstVersion = parameter.get("FirstVersion");
    String lastVersion = parameter.get("LastVersion");
    boolean isIncluded = StringUtils.equalsIgnoreCase(parameter.get("IsIncluded"), "true");
    String enforcement = StringUtils.defaultIfEmpty(parameter.get("Enforcement"), DEFAULT_ENFORCEMENT);

    try {
      if (StringUtils.isBlank(moduleId)) {
        throw new OBException("ModuleID parameter is required");
      }
      if (StringUtils.isBlank(dependsOnModuleId) && StringUtils.isBlank(dependsOnJavaPackage)) {
        throw new OBException("Either DependsOnModuleID or DependsOnJavaPackage parameter is required");
      }

      Module module = Utils.getModuleByID(moduleId);
      Module dependsOnModule = resolveDependsOnModule(dependsOnModuleId, dependsOnJavaPackage);

      checkDependencyNotExists(module, dependsOnModule);

      OBContext.setAdminMode(true);
      try {
        ModuleDependency dep = createDependency(module, dependsOnModule,
            firstVersion, lastVersion, isIncluded, enforcement);
        OBDal.getInstance().flush();

        responseVars.put("message",
            String.format("Dependency added: '%s' depends on '%s' (version %s+)",
                module.getJavaPackage(),
                dependsOnModule.getJavaPackage(),
                StringUtils.defaultIfEmpty(firstVersion, "any")));
      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (Exception e) {
      LOG.error("Error adding module dependency: {}", e.getMessage(), e);
      responseVars.put("error", e.getMessage());
      OBDal.getInstance().getSession().clear();
    }
  }

  private Module resolveDependsOnModule(String dependsOnModuleId, String dependsOnJavaPackage) {
    if (StringUtils.isNotBlank(dependsOnModuleId)) {
      Module m = OBDal.getInstance().get(Module.class, dependsOnModuleId);
      if (m == null) {
        throw new OBException(String.format("Module with ID '%s' not found", dependsOnModuleId));
      }
      return m;
    }
    return Utils.getModuleByJavaPackage(dependsOnJavaPackage);
  }

  private void checkDependencyNotExists(Module module, Module dependsOnModule) {
    OBCriteria<ModuleDependency> crit = OBDal.getInstance().createCriteria(ModuleDependency.class);
    crit.add(Restrictions.eq(ModuleDependency.PROPERTY_MODULE, module));
    crit.add(Restrictions.eq(ModuleDependency.PROPERTY_DEPENDENTMODULE, dependsOnModule));
    crit.setMaxResults(1);
    if (crit.uniqueResult() != null) {
      throw new OBException(
          String.format("Dependency from '%s' to '%s' already exists",
              module.getJavaPackage(), dependsOnModule.getJavaPackage()));
    }
  }

  private ModuleDependency createDependency(Module module, Module dependsOnModule,
      String firstVersion, String lastVersion, boolean isIncluded, String enforcement) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    ModuleDependency dep = OBProvider.getInstance().get(ModuleDependency.class);
    dep.setNewOBObject(true);
    dep.setClient(client);
    dep.setOrganization(org);
    dep.setActive(true);
    dep.setCreationDate(new Date());
    dep.setCreatedBy(OBContext.getOBContext().getUser());
    dep.setUpdated(new Date());
    dep.setUpdatedBy(OBContext.getOBContext().getUser());
    dep.setModule(module);
    dep.setDependentModule(dependsOnModule);
    dep.setDependantModuleName(dependsOnModule.getName());
    dep.setIncluded(isIncluded);
    dep.setDependencyEnforcement(enforcement);

    if (StringUtils.isNotBlank(firstVersion)) {
      dep.setFirstVersion(firstVersion);
    }
    if (StringUtils.isNotBlank(lastVersion)) {
      dep.setLastVersion(lastVersion);
    }

    OBDal.getInstance().save(dep);
    LOG.info("Dependency created: '{}' depends on '{}'",
        module.getJavaPackage(), dependsOnModule.getJavaPackage());
    return dep;
  }
}
