package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.client.application.Process;
import org.openbravo.client.application.Parameter;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.client.application.ReportDefinition;
import org.openbravo.model.ad.ui.Menu;

public class ProcessDefinitionJasper extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";
  private static final String REPORT_JAVA_CLASS_NAME = "org.openbravo.client.application.report.BaseReportActionHandler";
  private static final String WEB_PATH_INDICATOR = "web/";
  private static final String FILE_EXTENTION = ".jrxml";
  private static final String OBUIAPP_REPORT = "OBUIAPP_Report";
  private static final String PROCESS_ACTION = "OBUIAPP_Process";
  private static final String DATA_ACCESS_LEVEL = "3";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing validation process");

    try {
      validateParameters(parameter);
      Process processDef = createProcessDefinition(
              parameter.get("Prefix"),
              parameter.get("SearchKey"),
              parameter.get("ReportName"),
              parameter.get("Description"),
              parameter.get("HelpComment")
      );

      createReportDefinition(processDef, parameter.get("ReportPath"));

      List<Map<String, String>> params = convertParameters(parameter.get("Parameters"));
      createParametersForProcess(processDef, params, parameter.get("Prefix"));

      createMenuEntry(processDef, parameter.get("ReportName"), parameter.get("Prefix"));

      responseVars.put("message", "The record was created successfully.");
    } catch (IllegalArgumentException e) {
      log.error("Validation error: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error executing validation process", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  private void validateParameters(Map<String, String> parameter) {
    if (isInvalidParameter(parameter.get("Prefix"))) {
      throw new IllegalArgumentException("The Prefix parameter is missing");
    }
    if (isInvalidParameter(parameter.get("SearchKey"))) {
      throw new IllegalArgumentException("The SearchKey parameter is missing");
    }
    if (isInvalidParameter(parameter.get("ReportName"))) {
      throw new IllegalArgumentException("The ReportName parameter is missing");
    }
    if (isInvalidParameter(parameter.get("ReportPath"))) {
      throw new IllegalArgumentException("The ReportPath parameter is missing");
    }

    String prefix = parameter.get("Prefix");
    validatePrefixExists(prefix);

    List<Map<String, String>> parameters = convertParameters(parameter.get("Parameters"));
    validateParametersFormat(parameters);

    String path = parameter.get("ReportPath");
    validatePathFormat(path);
  }

  private boolean isInvalidParameter(String parameter) {
    return StringUtils.isBlank(parameter);
  }

  private void validatePrefixExists(String prefix) {
    log.info("Validating prefix: " + prefix);
    OBCriteria<ModuleDBPrefix> criteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    criteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    criteria.createAlias(ModuleDBPrefix.PROPERTY_MODULE, "module");

    if (criteria.list().isEmpty()) {
      throw new IllegalArgumentException("The prefix does not exist in the database: " + prefix);
    }
  }

  private List<Map<String, String>> convertParameters(String parametersJson) {
    Gson gson = new Gson();
    return gson.fromJson(parametersJson, new TypeToken<List<Map<String, String>>>() {}.getType());
  }

  private void validateParametersFormat(List<Map<String, String>> paramList) {
    log.info("Validating parameters format");

    for (Map<String, String> param : paramList) {
      if (!param.containsKey("BD_NAME") || !param.containsKey("NAME") ||
              !param.containsKey("LENGTH") || !param.containsKey("SEQNO") ||
              !param.containsKey("REFERENCE")) {
        throw new IllegalArgumentException("Parameter format is incorrect: " + param);
      }
    }
  }

  private void validatePathFormat(String path) {
    log.info("Validating path format: " + path);
    if (!path.contains(WEB_PATH_INDICATOR) || !path.endsWith(FILE_EXTENTION)) {
      throw new IllegalArgumentException("The path format is incorrect. It should contain '/web/' and end with '.jrxml': " + path);
    }
  }

  public Process createProcessDefinition(String prefix, String searchKey, String reportName, String description, String helpComment) {
    OBContext.setAdminMode(true);
    try {
      Process processDef = OBProvider.getInstance().get(Process.class);

      processDef.setNewOBObject(true);
      processDef.setModule(getModuleByPrefix(prefix));
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

  private Module getModuleByPrefix(String prefix) {
    OBCriteria<ModuleDBPrefix> criteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    criteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    criteria.setMaxResults(1);
    ModuleDBPrefix dbPrefix = (ModuleDBPrefix) criteria.uniqueResult();
    return dbPrefix != null ? dbPrefix.getModule() : null;
  }

  private void createParametersForProcess(Process processDef, List<Map<String, String>> parameters, String prefix) {
    OBContext.setAdminMode(true);
    try {
      for (Map<String, String> param : parameters) {
        Parameter parameter = OBProvider.getInstance().get(Parameter.class);

        // We assume default client and organization are set somehow
        // Adjust these lines according to your context
        parameter.setClient(OBContext.getOBContext().getCurrentClient());
        parameter.setOrganization(OBContext.getOBContext().getCurrentOrganization());
        parameter.setActive(true); // Assuming new parameters should be active

        parameter.setObuiappProcess(processDef); // Correct method name for setting the process
        parameter.setModule(getModuleByPrefix(prefix)); // Set the module using the prefix
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

  private Reference getReference(String referenceName) {
    if (StringUtils.isEmpty(referenceName)) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NullReference"));
    }

    OBCriteria<Reference> criteria = OBDal.getInstance().createCriteria(Reference.class);
    criteria.add(Restrictions.ilike(Reference.PROPERTY_NAME, referenceName));
    criteria.setMaxResults(1);

    if (criteria.list().isEmpty()) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NullReference"));
    }

    return (Reference) criteria.uniqueResult();
  }

  public void createReportDefinition(Process processDef, String reportPath) {
    OBContext.setAdminMode(true);
    try {
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

  private void createMenuEntry(Process processDef, String reportName, String prefix) {
    OBContext.setAdminMode(true);
    try {
      Menu menu = OBProvider.getInstance().get(Menu.class);
      menu.setName(reportName);
      menu.setAction(PROCESS_ACTION); // Process Definition
      menu.setOBUIAPPProcessDefinition(processDef);
      menu.setOrganization(OBContext.getOBContext().getCurrentOrganization());
      menu.setClient(OBContext.getOBContext().getCurrentClient());
      menu.setModule(getModuleByPrefix(prefix));

      OBDal.getInstance().save(menu);
      OBDal.getInstance().flush();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

}
