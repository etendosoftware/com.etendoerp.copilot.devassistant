package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.generator.qdox.model.JavaPackage;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterProcessWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);

    String javaPackage = parameter.get("Javapackage");
    String name = parameter.get("Name");
    String searchKey = parameter.get("SearchKey");
    String description = parameter.get("Description");
    String help = parameter.get("Help");



    /*if (javaPackage == null || Objects.equals(javaPackage, "null")) {
      javaClass = StringUtils.replaceChars(name, "_", " ");
      String[] words = javaClass.split(" ");
      StringBuilder formattedName = new StringBuilder();
      for (String word : words) {
        if (!StringUtils.isEmpty(word)) {
          formattedName.append(Character.toUpperCase(word.charAt(0)));
          formattedName.append(word.substring(1));
        }
      }
      javaClass = formattedName.toString();
    }*/

    try {
      //alreadyExistTable(tableName);

      Module module = getModule(javaPackage);
      String javaClassName = javaPackage + ".process." + name;

      Process process = createAdProcess(module, name, description, help, javaClassName, searchKey);

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
      String searchKey) {

    Process adProcess = OBProvider.getInstance().get(Process.class);
    adProcess.setNewOBObject(true);
    Client client = OBDal.getInstance().get(Client.class, "0");
    adProcess.setClient(client);
    adProcess.setActive(true);
    adProcess.setCreationDate(new Date());
    adProcess.setCreatedBy(OBContext.getOBContext().getUser());
    adProcess.setUpdated(new Date());
    adProcess.setUpdatedBy(OBContext.getOBContext().getUser());
    adProcess.setDataAccessLevel("All");
    adProcess.setModule(module);
    adProcess.setName(name);
    adProcess.setSearchKey(searchKey);
    adProcess.setJavaClassName(javaClassName);

    adProcess.setDescription(description);
    adProcess.setHelpComment(help);

    OBDal.getInstance().save(adProcess);
    OBDal.getInstance().flush();

    return adProcess;
  }


  private boolean alreadyExistTable(String tableName) {
    OBCriteria<Table> tableNameCrit = OBDal.getInstance().createCriteria(Table.class);
    tableNameCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, tableName));
    tableNameCrit.setMaxResults(1);
    Table tableExist = (Table) tableNameCrit.uniqueResult();

    if (tableExist != null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_TableNameAlreadyUse")));
    }
    return true;
  }
}

