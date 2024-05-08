package com.etendoerp.copilot.devassistant.webhooks;

import com.etendoerp.webhookevents.services.BaseWebhookService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class RegisterTable extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      log.info("Parameter: " + entry.getKey() + " = " + entry.getValue());
    }

    String dbPrefix = parameter.get("DBPrefix");
    String javaClass = parameter.get("JavaClass");
    String name = parameter.get("Name");
    String description = parameter.get("Description");

    if (javaClass == null) {
      javaClass = dbPrefix.toUpperCase() + name.replace("_", "");
    }

    //lectura de datapackage
    try {
      DataPackage dataPackage = getDataPackage(dbPrefix);
      Table adTable = createAdTable(dataPackage, javaClass, dbPrefix + "_" + name, description);
      responseVars.put("message",
          String.format("Table registered successfully in Etendo with the ID: '%s'.", adTable.getId()));
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private Table createAdTable(DataPackage dataPackage, String javaclass, String tableName, String description) {
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
    adTable.setDataAccessLevel("3");
    adTable.setDataPackage(dataPackage);
    adTable.setName(tableName);
    adTable.setJavaClassName(javaclass);
    adTable.setDescription(description);
    adTable.setDBTableName(tableName);

    OBDal.getInstance().save(adTable);

    OBDal.getInstance().flush();
    return adTable;


  }

  private DataPackage getDataPackage(String dbprefix) {
    OBCriteria<ModuleDBPrefix> modPrefCrit = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    modPrefCrit.add(Restrictions.ilike(ModuleDBPrefix.PROPERTY_NAME, dbprefix));
    modPrefCrit.setMaxResults(1);
    ModuleDBPrefix modPref = (ModuleDBPrefix) modPrefCrit.uniqueResult();

    if (modPref == null) {
      throw new OBException("The prefix does not exist.");
    }

    Module module = modPref.getModule();
    if (!module.isInDevelopment()) {
      throw new OBException("The module is not in development.");
    }
    List<DataPackage> dataPackList = module.getDataPackageList();
    if (dataPackList.isEmpty()) {
      throw new OBException("The module has not a datapackage.");
    }
    return dataPackList.get(0);
  }
}