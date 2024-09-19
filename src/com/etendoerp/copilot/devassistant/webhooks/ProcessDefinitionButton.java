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
import org.openbravo.client.application.Parameter;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.ModuleDBPrefix;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * This class handles the registration of process definitions using the webhook system.
 * It extends the BaseWebhookService class, providing the necessary methods for processing
 * the parameters and creating the required process and its parameters.
 */
public class ProcessDefinitionButton extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  // Constants used in the parameter map and error handling
  private static final String ERROR_PROPERTY = "error";
  private static final String PARAM_PREFIX = "Prefix";
  private static final String PARAM_SEARCH_KEY = "SearchKey";
  private static final String JAVA_PACKAGE = "JavaPackage";
  private static final String PARAM_PROCESS_NAME = "ProcessName";
  private static final String PARAM_DESCRIPTION = "Description";
  private static final String PARAM_HELP_COMMENT = "HelpComment";
  private static final String PARAM_PARAMETERS = "Parameters";
  private static final String PROCESS_ACTION = "OBUIAPP_PickAndExecute";
  private static final String DATA_ACCESS_LEVEL = "3";

  /**
   * Main entry point for the webhook when triggered by a GET request.
   * It validates the parameters, creates the process definition, and registers its parameters.
   *
   * @param parameter    The parameters passed through the webhook request.
   * @param responseVars The response map to store messages or error information.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    log.debug("Starting Process Definition registration");
    try {
      validateParameters(parameter);
      Process processDef = createProcessDefinition(
          parameter.get(PARAM_PREFIX),
          parameter.get(PARAM_SEARCH_KEY),
          parameter.get(PARAM_PROCESS_NAME),
          parameter.get(PARAM_DESCRIPTION),
          parameter.get(PARAM_HELP_COMMENT),
          parameter.get(JAVA_PACKAGE)
      );

      List<Map<String, String>> params = convertParameters(parameter.get(PARAM_PARAMETERS));
      createParametersForProcess(processDef, params, parameter.get(PARAM_PREFIX));
      responseVars.put("message", OBMessageUtils.getI18NMessage("COPDEV_RecordCreated"));

    } catch (IllegalArgumentException e) {
      log.error("Validation error: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error creating process definition", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  /**
   * Validates the incoming parameters, checking for missing or invalid values.
   *
   * @param parameter The map of incoming parameters to validate.
   * @throws IllegalArgumentException if any required parameter is missing or invalid.
   */
  private void validateParameters(Map<String, String> parameter) {
    String missingParameterMessage = "COPDEV_MissingParameter";

    if (isInvalidParameter(parameter.get(PARAM_PREFIX))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_PREFIX }));
    }

    if (isInvalidParameter(parameter.get(PARAM_SEARCH_KEY))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_SEARCH_KEY }));
    }

    if (isInvalidParameter(parameter.get(PARAM_PROCESS_NAME))) {
      throw new IllegalArgumentException(
          OBMessageUtils.getI18NMessage(missingParameterMessage, new String[]{ PARAM_PROCESS_NAME }));
    }

    validatePrefixExists(parameter.get(PARAM_PREFIX));
    validateParametersFormat(convertParameters(parameter.get(PARAM_PARAMETERS)));
  }

  /**
   * Checks if the given parameter is invalid (null or empty).
   *
   * @param parameter The parameter to check.
   * @return true if the parameter is invalid, false otherwise.
   */
  private boolean isInvalidParameter(String parameter) {
    return StringUtils.isBlank(parameter);
  }

  /**
   * Validates that the given prefix exists in the database.
   *
   * @param prefix The prefix to check.
   * @throws IllegalArgumentException if the prefix does not exist.
   */
  private void validatePrefixExists(String prefix) {
    OBCriteria<ModuleDBPrefix> criteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    criteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    criteria.setMaxResults(1);

    if (criteria.uniqueResult() == null) {
      throw new IllegalArgumentException(OBMessageUtils.getI18NMessage("COPDEV_NonExistentPrefix",
          new String[]{ prefix }));
    }
  }

  /**
   * Converts the parameter JSON string to a list of parameter maps.
   *
   * @param parametersJson The JSON string representing the parameters.
   * @return A list of parameter maps.
   */
  private List<Map<String, String>> convertParameters(String parametersJson) {
    Gson gson = new Gson();
    return gson.fromJson(parametersJson, new TypeToken<List<Map<String, String>>>() {
    }.getType());
  }

  /**
   * Builds the Java class name for the process handler based on the package and process name.
   *
   * @param javaPackage The package name.
   * @param processName The process name.
   * @return The fully qualified Java class name for the process handler.
   */
  private String buildReportJavaClassName(String javaPackage, String processName) {
    String processClassName = processName.replaceAll(" ", "");
    return javaPackage + ".actionHandler." + processClassName + "ActionHandler";
  }

  /**
   * Validates the format of the parameters, ensuring all required fields are present.
   *
   * @param paramList The list of parameters to validate.
   * @throws IllegalArgumentException if any parameter is missing required fields.
   */
  private void validateParametersFormat(List<Map<String, String>> paramList) {
    for (Map<String, String> param : paramList) {
      if (!param.containsKey("BD_NAME") || !param.containsKey("NAME") || !param.containsKey("LENGTH")
          || !param.containsKey("SEQNO") || !param.containsKey("REFERENCE")) {
        throw new IllegalArgumentException("Incorrect parameter format: " + param);
      }
    }
  }

  /**
   * Creates a new process definition and registers it in the system.
   *
   * @param prefix      The module prefix.
   * @param searchKey   The search key for the process.
   * @param processName The name of the process.
   * @param description The description of the process.
   * @param helpComment A help comment for the process.
   * @param javaPackage The Java package for the process handler class.
   * @return The created Process object.
   */
  public Process createProcessDefinition(String prefix, String searchKey, String processName, String description,
      String helpComment, String javaPackage) {
    try {
      OBContext.setAdminMode(true);
      Process processDef = OBProvider.getInstance().get(Process.class);
      processDef.setNewOBObject(true);
      processDef.setModule(Utils.getModuleByPrefix(prefix));
      processDef.setSearchKey(searchKey);
      processDef.setName(processName);
      processDef.setDescription(description);
      processDef.setHelpComment(helpComment);
      processDef.setUIPattern(PROCESS_ACTION);
      processDef.setDataAccessLevel(DATA_ACCESS_LEVEL);

      String reportJavaClassName = buildReportJavaClassName(javaPackage, processName);
      processDef.setJavaClassName(reportJavaClassName);
      processDef.setActive(true);

      OBDal.getInstance().save(processDef);
      OBDal.getInstance().flush();

      return processDef;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Creates and registers parameters for the given process definition.
   *
   * @param processDef The process definition to which the parameters are linked.
   * @param parameters The list of parameters to register.
   * @param prefix     The module prefix.
   */
  private void createParametersForProcess(Process processDef, List<Map<String, String>> parameters, String prefix) {
    try {
      OBContext.setAdminMode(true);
      for (Map<String, String> param : parameters) {
        Parameter parameter = OBProvider.getInstance().get(Parameter.class);
        parameter.setClient(OBContext.getOBContext().getCurrentClient());
        parameter.setOrganization(OBContext.getOBContext().getCurrentOrganization());
        parameter.setActive(true);
        parameter.setObuiappProcess(processDef);
        parameter.setModule(Utils.getModuleByPrefix(prefix));
        parameter.setDBColumnName(param.get("BD_NAME"));
        parameter.setName(param.get("NAME"));
        parameter.setSequenceNumber(Long.parseLong(param.get("SEQNO")));
        parameter.setLength(Long.parseLong(param.get("LENGTH")));
        parameter.setReference(getReference(param.get("REFERENCE")));

        OBDal.getInstance().save(parameter);
      }
      OBDal.getInstance().flush();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Retrieves a `Reference` object from the database based on the given reference name.
   * If the reference name is null or empty, or if no matching reference is found, an exception is thrown.
   *
   * @param referenceName The name of the reference to be searched in the database.
   * @return The `Reference` object that matches the given name.
   * @throws OBException if the reference name is empty or if no matching reference is found.
   */
  private Reference getReference(String referenceName) {
    if (StringUtils.isEmpty(referenceName)) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NullReference"));
    }

    OBCriteria<Reference> criteria = OBDal.getInstance().createCriteria(Reference.class);
    criteria.add(Restrictions.ilike(Reference.PROPERTY_NAME, referenceName));
    criteria.setMaxResults(1);

    Reference result = (Reference) criteria.uniqueResult();
    if (result == null) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NullReference"));
    }

    return result;
  }
}