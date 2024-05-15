package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import com.etendoerp.webhookevents.services.BaseWebhookService;

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
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RegisterTable extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    String dbPrefix = parameter.get("DBPrefix");
    String javaClass = parameter.get("JavaClass");
    String name = parameter.get("Name");
    String dalevel = parameter.get("DataAccessLevel");
    String description = parameter.get("Description");
    String _help = parameter.get("Help");

    String tableName = dbPrefix + "_" + name;

    if (javaClass == null || Objects.equals(javaClass, "null")) {
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
    }

    try {
      alreadyExistTable(tableName);
      DataPackage dataPackage = getDataPackage(dbPrefix);
      Table adTable = createAdTable(dataPackage, javaClass, tableName, dalevel, description, _help);
      responseVars.put("message",
          String.format(OBMessageUtils.messageBD("COPDEV_TableRegistSucc"), adTable.getId()));
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }



  private Table createAdTable(DataPackage dataPackage, String javaclass, String tableName, String dalevel, String
      description, String _help) {
    Table adTable = OBProvider.getInstance().get(Table.class);
    adTable.setNewOBObject(true);
    Client client = OBDal.getInstance().get(Client.class, "0");
    adTable.setClient(client);
    adTable.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
    adTable.setActive(true);
    adTable.setCreationDate(new Date());
    adTable.setCreatedBy(OBContext.getOBContext().getUser());
    adTable.setUpdated(new Date());
    adTable.setUpdatedBy(OBContext.getOBContext().getUser());
    adTable.setDataAccessLevel(dalevel);
    adTable.setDataPackage(dataPackage);
    adTable.setName(tableName);
    adTable.setJavaClassName(javaclass);
    adTable.setDescription(description);
    adTable.setHelpComment(_help);
    adTable.setDBTableName(tableName);
    OBDal.getInstance().save(adTable);
    OBDal.getInstance().flush();

    return adTable;
  }


  private boolean alreadyExistTable(String tableName) {
    OBCriteria<Table> tableNameCrit = OBDal.getInstance().createCriteria(Table.class);
    tableNameCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, tableName));
    tableNameCrit.setMaxResults(1);
    Table tableExist = (Table) tableNameCrit.uniqueResult();

    if (tableExist != null) {
      throw new OBException("The table name is already in use.");
    }
    return true;
  }


  private DataPackage getDataPackage(String dbPrefix) {

    OBCriteria<ModuleDBPrefix> modPrefCrit = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    modPrefCrit.add(Restrictions.ilike(ModuleDBPrefix.PROPERTY_NAME, dbPrefix));
    modPrefCrit.setMaxResults(1);
    ModuleDBPrefix modPref = (ModuleDBPrefix) modPrefCrit.uniqueResult();
    if (modPref == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_PrefixNotFound"), dbPrefix));
    }
    Module module = modPref.getModule();
    if (!module.isInDevelopment()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDev"), module.getName()));
    }
    List<DataPackage> dataPackList = module.getDataPackageList();
    if (dataPackList.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDP"), module.getName()));
    }
    return dataPackList.get(0);
  }
}

