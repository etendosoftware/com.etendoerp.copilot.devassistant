package com.etendoerp.copilot.devassistant.webhooks;

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
import org.openbravo.client.application.ReportDefinition;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.ui.Menu;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * This class handles the creation of Jasper report processes triggered by webhooks.
 */
public class ProcessDefinitionJasper extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  // Error property for response messages
  public static final String ERROR_PROPERTY = "error";

  // Parameter keys
  private static final String PARAM_PREFIX = "Prefix";
  private static final String PARAM_SEARCH_KEY = "SearchKey";
  private static final String PARAM_REPORT_NAME = "ReportName";
  private static final String PARAM_DESCRIPTION = "Description";
  private static final String PARAM_HELP_COMMENT = "HelpComment";
  private static final String PARAM_REPORT_PATH = "ReportPath";
  private static final String PARAM_PARAMETERS = "Parameters";

  // Other constants
  private static final String REPORT_JAVA_CLASS_NAME = "org.openbravo.client.application.report.BaseReportActionHandler";
  private static final String WEB_PATH_INDICATOR = "web/";
  private static final String FILE_EXTENTION = ".jrxml";
  private static final String OBUIAPP_REPORT = "OBUIAPP_Report";
  private static final String PROCESS_ACTION = "OBUIAPP_Process";
  private static final String DATA_ACCESS_LEVEL = "3";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.debug("Executing validation process");

    try {
      validateParameters(parameter);
      Process processDef = createProcessDefinition(
          parameter.get(PARAM_PREFIX),
          parameter.get(PARAM_SEARCH_KEY),
          parameter.get(PARAM_REPORT_NAME),
          parameter.get(PARAM_DESCRIPTION),
          parameter.get(PARAM_HELP_COMMENT)
      );

      createReportDefinition(processDef, parameter.get(PARAM_REPORT_PATH));

      List<Map<String, String>> params = convertParameters(parameter.get(PARAM_PARAMETERS));
      createParametersForProcess(processDef, params, parameter.get(PARAM_PREFIX));

      createMenuEntry(processDef, parameter.get(PARAM_REPORT_NAME), parameter.get(PARAM_PREFIX));

      responseVars.put("message", OBMessageUtils.getI18NMessage("COPDEV_RecordCreated"));

    } catch (IllegalArgumentException e) {
      log.error("Validation error: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error executing validation process", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  /**
   * Validates the parameters required for creating a report process definition.
   *
   * @param parameter
   *     the map of parameters
   * @throws IllegalArgumentException
   *     if any required parameter is missing or invalid
   */
  private void validateParameters(Map<String, String> parameter) {
    String missingParameterMessage = "COPDEV_MissingParameter";
    if (isInvalidParameter(parameter.get(PARAM_PREFIX))) {
      throw new IllegalArgumentException(OBMessageUtils.getI18NMessage(missingParameterMessage,
          new String[]{ PARAM_PREFIX }));
    }

    if (isInvalidParameter(parameter.get(PARAM_SEARCH_KEY))) {
      throw new IllegalArgumentException(OBMessageUtils.getI18NMessage(missingParameterMessage,
          new String[]{ PARAM_SEARCH_KEY }));
    }
    if (isInvalidParameter(parameter.get(PARAM_REPORT_NAME))) {
      throw new IllegalArgumentException(OBMessageUtils.getI18NMessage(missingParameterMessage,
          new String[]{ PARAM_REPORT_NAME }));
    }
    if (isInvalidParameter(parameter.get(PARAM_REPORT_PATH))) {
      throw new IllegalArgumentException(OBMessageUtils.getI18NMessage(missingParameterMessage,
          new String[]{ PARAM_REPORT_PATH }));
    }

    String prefix = parameter.get(PARAM_PREFIX);
    validatePrefixExists(prefix);

    List<Map<String, String>> parameters = convertParameters(parameter.get(PARAM_PARAMETERS));
    validateParametersFormat(parameters);

    String path = parameter.get(PARAM_REPORT_PATH);
    validatePathFormat(path);
  }

  /**
   * Checks if a parameter is invalid (null or empty).
   *
   * @param parameter
   *     the parameter value
   * @return true if the parameter is invalid, false otherwise
   */
  private boolean isInvalidParameter(String parameter) {
    return StringUtils.isBlank(parameter);
  }

  /**
   * Validates that the provided prefix exists in the database.
   *
   * @param prefix
   *     the prefix to validate
   * @throws OBException
   *     if the prefix does not exist
   */
  private void validatePrefixExists(String prefix) {
    log.debug("Validating prefix: {}", prefix);
    OBCriteria<ModuleDBPrefix> criteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    criteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    criteria.createAlias(ModuleDBPrefix.PROPERTY_MODULE, "module");

    criteria.setMaxResults(1);
    ModuleDBPrefix result = (ModuleDBPrefix) criteria.uniqueResult();

    if (result == null) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NonExistentPrefix",
          new String[]{ prefix }));
    }
  }

  /**
   * Converts a JSON string into a list of parameter maps.
   *
   * @param parametersJson
   *     the JSON string representing the parameters
   * @return the list of parameters
   */
  private List<Map<String, String>> convertParameters(String parametersJson) {
    Gson gson = new Gson();
    return gson.fromJson(parametersJson, new TypeToken<List<Map<String, String>>>() {
    }.getType());
  }

  /**
   * Validates the format of the given parameters.
   *
   * @param paramList
   *     the list of parameters
   * @throws IllegalArgumentException
   *     if any parameter has an incorrect format
   */
  private void validateParametersFormat(List<Map<String, String>> paramList) {
    log.debug("Validating parameters format");

    for (Map<String, String> param : paramList) {
      if (!param.containsKey("BD_NAME") || !param.containsKey("NAME") ||
          !param.containsKey("LENGTH") || !param.containsKey("SEQNO") ||
          !param.containsKey("REFERENCE")) {
        throw new IllegalArgumentException("Parameter format is incorrect: " + param);
      }
    }
  }

  /**
   * Validates the format of the report path.
   *
   * @param path
   *     the report path
   * @throws IllegalArgumentException
   *     if the path format is incorrect
   */
  private void validatePathFormat(String path) {
    log.debug("Validating path format: {}", path);
    if (!StringUtils.contains(path, WEB_PATH_INDICATOR) || !StringUtils.endsWith(path, FILE_EXTENTION)) {
      throw new IllegalArgumentException(OBMessageUtils.getI18NMessage("COPDEV_IncorrectFormat",
          new String[]{ path }));
    }
  }

  /**
   * Creates a process definition and saves it to the database.
   *
   * @param prefix
   *     the module prefix
   * @param searchKey
   *     the search key
   * @param reportName
   *     the report name
   * @param description
   *     the report description
   * @param helpComment
   *     the help comment
   * @return the created Process object
   */
  public Process createProcessDefinition(String prefix, String searchKey, String reportName, String description,
      String helpComment) {
    try {
      OBContext.setAdminMode(true);
      Process processDef = OBProvider.getInstance().get(Process.class);

      processDef.setNewOBObject(true);
      processDef.setModule(Utils.getModuleByPrefix(prefix));
      processDef.setSearchKey(searchKey);
      processDef.setName(reportName);
      processDef.setDescription(description);
      processDef.setHelpComment(helpComment);
      processDef.setUIPattern(OBUIAPP_REPORT);
      processDef.setDataAccessLevel(DATA_ACCESS_LEVEL);
      processDef.setJavaClassName(REPORT_JAVA_CLASS_NAME);
      processDef.setActive(true);

      OBDal.getInstance().save(processDef);
      OBDal.getInstance().flush();

      return processDef;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Creates parameters for the given process definition.
   *
   * @param processDef
   *     the Process object
   * @param parameters
   *     the list of parameters
   * @param prefix
   *     the module prefix
   */
  private void createParametersForProcess(Process processDef, List<Map<String, String>> parameters, String prefix) {
    try {
      OBContext.setAdminMode(true);
      for (Map<String, String> param : parameters) {
        Parameter parameter = OBProvider.getInstance().get(Parameter.class);

        // We assume default client and organization are set somehow
        // Adjust these lines according to your context
        parameter.setClient(OBContext.getOBContext().getCurrentClient());
        parameter.setOrganization(OBContext.getOBContext().getCurrentOrganization());
        parameter.setActive(true); // Assuming new parameters should be active

        parameter.setObuiappProcess(processDef); // Correct method name for setting the process
        parameter.setModule(Utils.getModuleByPrefix(prefix)); // Set the module using the prefix
        parameter.setDBColumnName(param.get("BD_NAME"));
        parameter.setName(param.get("NAME"));
        parameter.setSequenceNumber(Long.parseLong(param.get("SEQNO")));
        parameter.setLength(Long.parseLong(param.get("LENGTH")));

        Reference reference = getReference(param.get("REFERENCE"));
        parameter.setReference(reference);

        OBDal.getInstance().save(parameter);
      }
      OBDal.getInstance().flush();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Retrieves a Reference object by name.
   *
   * @param referenceName
   *     the name of the reference
   * @return the Reference object
   * @throws OBException
   *     if the reference is not found or the name is empty
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

  /**
   * Creates a report definition for the given process definition.
   *
   * @param processDef
   *     the Process object
   * @param reportPath
   *     the path to the report file
   */
  public void createReportDefinition(Process processDef, String reportPath) {
    try {
      OBContext.setAdminMode(true);
      ReportDefinition reportDefinition = OBProvider.getInstance().get(ReportDefinition.class);

      reportDefinition.setClient(OBContext.getOBContext().getCurrentClient());
      reportDefinition.setOrganization(OBContext.getOBContext().getCurrentOrganization());
      reportDefinition.setActive(true);
      reportDefinition.setProcessDefintion(processDef);
      reportDefinition.setPDFTemplate(reportPath);

      OBDal.getInstance().save(reportDefinition);
      OBDal.getInstance().flush();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Creates a menu entry for the given process definition.
   *
   * @param processDef
   *     the Process object
   * @param reportName
   *     the name of the report
   * @param prefix
   *     the module prefix
   */
  private void createMenuEntry(Process processDef, String reportName, String prefix) {

    try {
      OBContext.setAdminMode(true);
      Menu menu = OBProvider.getInstance().get(Menu.class);
      menu.setName(reportName);
      menu.setAction(PROCESS_ACTION); // Process Definition
      menu.setOBUIAPPProcessDefinition(processDef);
      menu.setOrganization(OBContext.getOBContext().getCurrentOrganization());
      menu.setClient(OBContext.getOBContext().getCurrentClient());
      menu.setModule(Utils.getModuleByPrefix(prefix));

      OBDal.getInstance().save(menu);
      OBDal.getInstance().flush();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

}
