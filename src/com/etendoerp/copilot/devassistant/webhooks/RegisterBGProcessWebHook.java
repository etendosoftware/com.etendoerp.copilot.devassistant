package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

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
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Process;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterBGProcessWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);

    String javaPackage = parameter.get("Javapackage");
    String name = parameter.get("Name");
    String searchKey = parameter.get("SearchKey");
    String description = parameter.get("Description");
    String help = parameter.get("Help");
    String preventConcurrentStr = parameter.get("PreventConcurrent");

    boolean preventConcurrentExec= StringUtils.isNotEmpty(preventConcurrentStr) && StringUtils.equalsIgnoreCase(preventConcurrentStr,"true");

    try {
      Module module = getModule(javaPackage);
      String javaClassName = javaPackage + ".background." + name;

      String dataAccessLevel = "7";

      Process process = createAdProcess(module, name, description, help, javaClassName, searchKey,preventConcurrentExec,
          dataAccessLevel);

      responseVars.put("message",
          String.format(OBMessageUtils.messageBD("COPDEV_ProcessRegisterSuccessfully"), process.getName()));
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private static Module getModule(String javaPackage) {
    OBCriteria<Module> modJavaCrit = OBDal.getInstance().createCriteria(Module.class);
    modJavaCrit.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, javaPackage));
    modJavaCrit.setMaxResults(1);
    return (Module) modJavaCrit.uniqueResult();
  }


  private Process createAdProcess(Module module, String name, String description, String help, String javaClassName,
      String searchKey, boolean prevConcExec, String dataAccessLevel) {

    Process adProcess = OBProvider.getInstance().get(Process.class);
    adProcess.setNewOBObject(true);

    OBContext obContext = OBContext.getOBContext();
    adProcess.setClient(obContext.getCurrentClient());
    adProcess.setOrganization(obContext.getCurrentOrganization());
    adProcess.setDataAccessLevel(dataAccessLevel);
    adProcess.setModule(module);
    adProcess.setName(name);
    adProcess.setSearchKey(searchKey);
    adProcess.setJavaClassName(javaClassName);
    adProcess.setBackground(true);
    adProcess.setUIPattern("M");
    adProcess.setPreventConcurrentExecutions(prevConcExec);

    adProcess.setDescription(description);
    adProcess.setHelpComment(help);

    OBDal.getInstance().save(adProcess);
    OBDal.getInstance().flush();

    return adProcess;
  }
}
