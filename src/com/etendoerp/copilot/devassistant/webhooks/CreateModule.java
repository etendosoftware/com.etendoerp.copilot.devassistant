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
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to create an Etendo module atomically: AD_MODULE + AD_MODULE_DBPREFIX + AD_PACKAGE.
 * Replaces the manual SQL flow for module creation.
 *
 * <p>Required parameters:
 * <ul>
 *   <li>{@code Name} — human-readable module name (e.g. "My Tutorial Module")</li>
 *   <li>{@code JavaPackage} — Java package (e.g. "com.smf.tutorial")</li>
 *   <li>{@code DBPrefix} — DB prefix in UPPERCASE (e.g. "SMFT")</li>
 * </ul>
 *
 * <p>Optional parameters:
 * <ul>
 *   <li>{@code Description} — module description (defaults to Name)</li>
 *   <li>{@code Version} — semantic version (defaults to "1.0.0")</li>
 *   <li>{@code Author} — author string (defaults to "")</li>
 *   <li>{@code Type} — module type: "M" (Module), "T" (Template), "P" (Pack) — defaults to "M"</li>
 * </ul>
 *
 * <p>Response: {@code {"message": "Module created successfully with ID: <module_id>"}}
 */
public class CreateModule extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String DEFAULT_VERSION = "1.0.0";
  private static final String DEFAULT_TYPE = "M";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);

    String name = parameter.get("Name");
    String javaPackage = parameter.get("JavaPackage");
    String dbPrefix = parameter.get("DBPrefix");
    String description = StringUtils.defaultIfEmpty(parameter.get("Description"), name);
    String version = StringUtils.defaultIfEmpty(parameter.get("Version"), DEFAULT_VERSION);
    String author = StringUtils.defaultIfEmpty(parameter.get("Author"), "");
    String type = StringUtils.defaultIfEmpty(parameter.get("Type"), DEFAULT_TYPE);

    try {
      validateRequiredParameters(name, javaPackage, dbPrefix);

      String normalizedPrefix = StringUtils.upperCase(dbPrefix);

      checkModuleNotExists(javaPackage, normalizedPrefix);

      OBContext.setAdminMode(true);
      try {
        Module module = createModule(name, javaPackage, description, version, author, type);
        createModuleDBPrefix(module, normalizedPrefix);
        createDataPackage(module);
        OBDal.getInstance().flush();

        responseVars.put("message",
            String.format("Module created successfully with ID: %s", module.getId()));
      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (Exception e) {
      LOG.error("Error creating module: {}", e.getMessage(), e);
      responseVars.put("error", e.getMessage());
      OBDal.getInstance().getSession().clear();
    }
  }

  private void validateRequiredParameters(String name, String javaPackage, String dbPrefix) {
    if (StringUtils.isBlank(name)) {
      throw new OBException("Name parameter is required");
    }
    if (StringUtils.isBlank(javaPackage)) {
      throw new OBException("JavaPackage parameter is required");
    }
    if (StringUtils.isBlank(dbPrefix)) {
      throw new OBException("DBPrefix parameter is required");
    }
  }

  private void checkModuleNotExists(String javaPackage, String dbPrefix) {
    OBCriteria<Module> moduleCrit = OBDal.getInstance().createCriteria(Module.class);
    moduleCrit.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, javaPackage));
    moduleCrit.setMaxResults(1);
    if (moduleCrit.uniqueResult() != null) {
      throw new OBException(
          String.format("Module with java package '%s' already exists", javaPackage));
    }

    OBCriteria<ModuleDBPrefix> prefixCrit = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    prefixCrit.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, dbPrefix));
    prefixCrit.setMaxResults(1);
    if (prefixCrit.uniqueResult() != null) {
      throw new OBException(
          String.format("DB prefix '%s' is already used by another module", dbPrefix));
    }
  }

  private Module createModule(String name, String javaPackage, String description,
      String version, String author, String type) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    Module module = OBProvider.getInstance().get(Module.class);
    module.setNewOBObject(true);
    module.setClient(client);
    module.setOrganization(org);
    module.setActive(true);
    module.setCreationDate(new Date());
    module.setCreatedBy(OBContext.getOBContext().getUser());
    module.setUpdated(new Date());
    module.setUpdatedBy(OBContext.getOBContext().getUser());
    module.setName(name);
    module.setJavaPackage(javaPackage);
    module.setDescription(description);
    module.setVersion(version);
    module.setAuthor(author);
    module.setType(type);
    module.setInDevelopment(true);
    module.setDefault(false);

    OBDal.getInstance().save(module);
    LOG.info("Module '{}' created with ID: {}", name, module.getId());
    return module;
  }

  private void createModuleDBPrefix(Module module, String dbPrefix) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    ModuleDBPrefix prefix = OBProvider.getInstance().get(ModuleDBPrefix.class);
    prefix.setNewOBObject(true);
    prefix.setClient(client);
    prefix.setOrganization(org);
    prefix.setActive(true);
    prefix.setModule(module);
    prefix.setName(dbPrefix);

    OBDal.getInstance().save(prefix);
    LOG.info("DB prefix '{}' created for module '{}'", dbPrefix, module.getName());
  }

  private void createDataPackage(Module module) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    DataPackage pkg = OBProvider.getInstance().get(DataPackage.class);
    pkg.setNewOBObject(true);
    pkg.setClient(client);
    pkg.setOrganization(org);
    pkg.setModule(module);
    pkg.setName(module.getName());
    pkg.setDescription(module.getName() + " Package");
    pkg.setJavaPackage(module.getJavaPackage());
    pkg.setActive(true);

    OBDal.getInstance().save(pkg);
    LOG.info("DataPackage created for module '{}'", module.getName());
  }
}
