package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
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
 * <p>
 * The webhook expects parameters like module name, Java package, description, help comments,
 * version, license, and database prefix, and validates them before registering the module.
 * <p>
 * The key steps in this webhook are:
 * <ul>
 *   <li>Validation of required parameters</li>
 *   <li>Module registration in the system</li>
 *   <li>Setting the module's dependencies</li>
 *   <li>Setting the database prefix and Java package</li>
 * </ul>
 * <p>
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
  private static final String PARAM_TYPE = "Type";
  private static final String CORE_DEPENDENCY = "0";
  private static final String DEPENDENCY_ENFORCEMENT = "MAJOR";
  private static final String DATA_PACKAGE_SUFFIX = " Data Package";
  private static final String MODULE = "M";
  private static final String TEMPLATE = "T";
  private static final String PACKAGE = "P";


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

  /**
   * Validates the required parameters for creating a module. If any required parameter is
   * missing or invalid, it throws an IllegalArgumentException.
   *
   * @param parameter
   *     The map containing the input parameters.
   * @throws IllegalArgumentException
   *     if any required parameter is missing.
   */
  private void validateParameters(Map<String, String> parameter) {
    List<String> allowedTypes = List.of(MODULE, TEMPLATE, PACKAGE);
    String missingParameterMessage = "COPDEV_MissingParameter";

    if (isInvalidParameter(parameter.get(PARAM_JAVA_PACKAGE))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_JAVA_PACKAGE }));
    }

    if (isInvalidParameter(parameter.get(PARAM_DESCRIPTION))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_DESCRIPTION }));
    }

    if (isInvalidParameter(parameter.get(PARAM_MODULE_NAME))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_MODULE_NAME }));
    }

    if (isInvalidParameter(parameter.get(PARAM_VERSION))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_VERSION }));
    }
    String type = parameter.get(PARAM_TYPE);
    if (!isInvalidParameter(type)) {
      if (!allowedTypes.contains(type)) {
        throw new IllegalArgumentException(
            OBMessageUtils.getI18NMessage("COPDEV_InvalidType", new String[]{ type }));
      }
    } else {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_TYPE }));
    }

  }

  /**
   * Checks if the provided parameter is invalid (null or empty).
   *
   * @param parameter
   *     The parameter to be checked.
   * @return true if the parameter is blank or null, false otherwise.
   */
  private boolean isInvalidParameter(String parameter) {
    return StringUtils.isBlank(parameter);
  }

  /**
   * Maps the provided license type string to a system-defined license identifier.
   *
   * @param license
   *     The string representing the license type.
   * @return The mapped license identifier.
   * @throws IllegalArgumentException
   *     if the license type is invalid.
   */
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
        throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_InvalidLicenseType",

            new String[]{ license }));
    }
  }

  /**
   * Creates the module definition using the provided parameters. It also sets up dependencies
   * and database prefix associated with the module.
   *
   * @param parameter
   *     The map containing the input parameters.
   */
  private void createModuleDefinition(Map<String, String> parameter) {
    try {
      OBContext.setAdminMode(true);
      Module moduleDef = createModuleHeader(parameter);
      createDependencyModule(CORE_DEPENDENCY, moduleDef);
      if (StringUtils.equals(PARAM_TYPE, MODULE)) {
        String dbPrefixValue = parameter.get(PARAM_DBPREFIX);
        createDBPrefixModule(moduleDef, dbPrefixValue);
        createDataPackageModule(moduleDef, parameter.get(PARAM_JAVA_PACKAGE));
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Creates the header of the module, filling in the module's metadata, including name,
   * description, version, license, and Java package. It then saves the module to the database.
   *
   * @param parameter
   *     The map containing the input parameters.
   * @return The created module instance.
   */
  private Module createModuleHeader(Map<String, String> parameter) {
    String license = mapLicenseType(parameter.get(PARAM_LICENSE));
    String javaPackage = parameter.get(PARAM_JAVA_PACKAGE);
    String moduleName = parameter.get(PARAM_MODULE_NAME);
    String description = parameter.get(PARAM_DESCRIPTION);
    String helpComment = parameter.get(PARAM_HELP_COMMENT);
    String version = parameter.get(PARAM_VERSION);
    String type = parameter.get(PARAM_TYPE);

    Module moduleDef = OBProvider.getInstance().get(Module.class);
    moduleDef.setNewOBObject(true);
    moduleDef.setDescription(description);
    moduleDef.setHelpComment(helpComment);
    moduleDef.setVersion(version);
    moduleDef.setLicenseType(license);
    moduleDef.setActive(true);
    moduleDef.setType(type);
    if (StringUtils.equals(type, MODULE)) {
      moduleDef.setJavaPackage(javaPackage);
      moduleDef.setName(moduleName);
    } else if (StringUtils.equals(type, TEMPLATE)) {
      moduleDef.setJavaPackage(javaPackage + ".template");
      moduleDef.setName(moduleName + " Template");
    }

    OBDal.getInstance().save(moduleDef);
    OBDal.getInstance().flush();
    return moduleDef;
  }

  /**
   * Creates a module dependency for the provided module, linking it to the specified dependent
   * module. The dependency enforcement is set to MAJOR.
   *
   * @param moduleId
   *     The ID of the dependent module.
   * @param moduleDef
   *     The module for which the dependency is being created.
   * @throws OBException
   *     if the dependent module is not found.
   */
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

  /**
   * Retrieves a module by its ID.
   *
   * @param moduleId
   *     The ID of the module to retrieve.
   * @return The module object, or null if not found.
   */
  private Module getModuleById(String moduleId) {
    return OBDal.getInstance().get(Module.class, moduleId);
  }

  /**
   * Creates the database prefix entry for the module, linking it to the module definition.
   *
   * @param moduleDef
   *     The module for which the database prefix is being created.
   * @param dbprefix
   *     The database prefix string.
   */
  private void createDBPrefixModule(Module moduleDef, String dbprefix) {
    if (existingDBPrefix(dbprefix)) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_DBPREFIXInUse", new String[]{ dbprefix }));
    }
    ModuleDBPrefix moduleDBPrefix = OBProvider.getInstance().get(ModuleDBPrefix.class);
    moduleDBPrefix.setModule(moduleDef);
    moduleDBPrefix.setName(dbprefix);
    OBDal.getInstance().save(moduleDBPrefix);
    OBDal.getInstance().flush();

  }

  /**
   * Checks whether a given database prefix exists in the system.
   * <p>
   * This method queries the database to determine if there is any record of the
   * {@link ModuleDBPrefix} entity that matches the provided prefix. The query is restricted
   * to return only one result. If a matching record is found, the method returns {@code true},
   * otherwise it returns {@code false}.
   *
   * @param dbprefix
   *     the database prefix to be checked.
   * @return {@code true} if a record with the specified database prefix exists,
   *     {@code false} otherwise.
   */
  private boolean existingDBPrefix(String dbprefix) {
    OBCriteria<ModuleDBPrefix> dbPrefixCrit = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    dbPrefixCrit.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, dbprefix));
    dbPrefixCrit.setMaxResults(1);
    ModuleDBPrefix dbPrefixRecord = (ModuleDBPrefix) dbPrefixCrit.uniqueResult();

    return dbPrefixRecord != null;
  }

  /**
   * Creates a new Data Package associated with the provided module definition.
   * <p>
   * This method is responsible for creating a data package entry for the module being registered.
   * The data package is linked to the module and contains the module's name along with a suffix
   * for easy identification. The Java package for the data package is also specified by appending
   * ".data" to the given Java package name.
   * <p>
   * After setting the required properties, the data package is saved to the database using
   * Openbravo's Data Access Layer (DAL).
   *
   * @param moduleDef
   *     the {@link Module} object representing the module being created.
   * @param javaPackage
   *     the base Java package name associated with the module, to which ".data"
   *     will be appended for the data package.
   */
  private void createDataPackageModule(Module moduleDef, String javaPackage) {
    DataPackage dataPackage = OBProvider.getInstance().get(DataPackage.class);

    dataPackage.setModule(moduleDef);
    dataPackage.setName(moduleDef.getName() + DATA_PACKAGE_SUFFIX);
    dataPackage.setJavaPackage(javaPackage + ".data");
    OBDal.getInstance().save(dataPackage);
    OBDal.getInstance().flush();
  }
}