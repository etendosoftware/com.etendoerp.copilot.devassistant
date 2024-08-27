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

public class ProcessDefinitionJasper extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";
  private static final String REPORT_JAVA_CLASS_NAME = "org.openbravo.client.application.report.BaseReportActionHandler";

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

      List<Map<String, String>> params = convertParameters(parameter.get("Parameters"));
      createParametersForProcess(processDef, params, parameter.get("Prefix"));

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
    if (StringUtils.isBlank(parameter.get("Prefix"))) {
      throw new IllegalArgumentException("The Prefix parameter is missing");
    }
    if (StringUtils.isBlank(parameter.get("SearchKey"))) {
      throw new IllegalArgumentException("The SearchKey parameter is missing");
    }
    if (StringUtils.isBlank(parameter.get("ReportName"))) {
      throw new IllegalArgumentException("The ReportName parameter is missing");
    }
    if (StringUtils.isBlank(parameter.get("ReportPath"))) {
      throw new IllegalArgumentException("The ReportPath parameter is missing");
    }

    String prefix = parameter.get("Prefix");
    validatePrefixExists(prefix);

    List<Map<String, String>> parameters = convertParameters(parameter.get("Parameters"));
    validateParametersFormat(parameters);

    String path = parameter.get("ReportPath");
    validatePathFormat(path);
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
    if (!path.contains("/web/") || !path.endsWith(".jrxml")) {
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
      processDef.setUIPattern("OBUIAPP_Report");
      processDef.setDataAccessLevel("3");
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
    OBCriteria<ModuleDBPrefix> moduleDBPrefixOBCriteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    moduleDBPrefixOBCriteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    moduleDBPrefixOBCriteria.setMaxResults(1);
    ModuleDBPrefix moduleDBPrefix = (ModuleDBPrefix) moduleDBPrefixOBCriteria.uniqueResult();
    return moduleDBPrefix != null ? moduleDBPrefix.getModule() : null;
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
        // Set reference and reference search key
        Reference reference = getReference(param.get("REFERENCE"));
        parameter.setReference(reference);

        parameter.setLength(Long.parseLong(param.get("LENGTH")));

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
    OBCriteria<Reference> refList = OBDal.getInstance().createCriteria(Reference.class);
    refList.add(Restrictions.ilike(Reference.PROPERTY_NAME, referenceName));
    refList.setMaxResults(1);

    if (refList.list().isEmpty()) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NullReference"));
    }
    return (Reference) refList.uniqueResult();
  }
}
