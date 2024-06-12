package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.domain.List;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class CreateReference extends BaseWebhookService {
  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";
  private static final String DEFAULT_PARENT_REFERENCE_ID = "17";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing process");
    try {
      validateParameters(parameter);

      String list = parameter.get("referenceList");
      String prefix = parameter.get("prefix");
      String name = parameter.get("nameReference");

      Reference newReference = createReference(name, prefix);
      createReferenceListItems(list, newReference);

      OBDal.getInstance().flush();
      responseVars.put("message", OBMessageUtils.messageBD("COPDEV_ReferenceCreated"));
    } catch (IllegalArgumentException e) {
      log.error("Missing parameter: ", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    } catch (Exception e) {
      log.error("Error executing process", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  private void validateParameters(Map<String, String> parameter) {
    if (parameter.get("referenceList") == null) {
      throw new IllegalArgumentException("referenceList parameter is missing");
    }
    if (parameter.get("prefix") == null) {
      throw new IllegalArgumentException("prefix parameter is missing");
    }
    if (parameter.get("nameReference") == null) {
      throw new IllegalArgumentException("nameReference parameter is missing");
    }
  }

  private Reference createReference(String name, String prefix) {
    Reference newReference = OBProvider.getInstance().get(Reference.class);
    newReference.setNewOBObject(true);
    newReference.setName(name);
    newReference.setModule(getModuleByPrefix(prefix));
    newReference.setParentReference(OBDal.getInstance().get(Reference.class, DEFAULT_PARENT_REFERENCE_ID));
    OBDal.getInstance().save(newReference);
    return newReference;
  }

  private void createReferenceListItems(String list, Reference reference) {
    String[] referenceItems = list.split(",");
    for (int i = 0; i < referenceItems.length; i++) {
      generateReferenceListRecord(referenceItems[i].trim(), i + 1, reference);
    }
  }

  private void generateReferenceListRecord(String name, int index, Reference reference) {
    List newReferenceList = OBProvider.getInstance().get(List.class);
    newReferenceList.setNewOBObject(true);
    newReferenceList.setName(name);
    newReferenceList.setSearchKey(name.substring(0, 1) + index);
    newReferenceList.setReference(reference);
    newReferenceList.setModule(reference.getModule());
    OBDal.getInstance().save(newReferenceList);
  }

  private Module getModuleByPrefix(String prefix) {
    OBCriteria<ModuleDBPrefix> moduleDBPrefixOBCriteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    moduleDBPrefixOBCriteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    moduleDBPrefixOBCriteria.setMaxResults(1);
    ModuleDBPrefix moduleDBPrefix = (ModuleDBPrefix) moduleDBPrefixOBCriteria.uniqueResult();
    return moduleDBPrefix != null ? moduleDBPrefix.getModule() : null;
  }
}
