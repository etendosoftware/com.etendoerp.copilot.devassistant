package com.etendoerp.copilot.devassistant.webhooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
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
import org.apache.commons.lang.StringUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Class to create references through webhook services.
 * This class handles the creation of references and their items.
 * Requires the parameters: referenceList, prefix, and nameReference.
 */
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
    if (StringUtils.isBlank(parameter.get("referenceList"))) {
      throw new IllegalArgumentException("referenceList parameter is missing");
    }
    if (StringUtils.isBlank(parameter.get("prefix"))) {
      throw new IllegalArgumentException("prefix parameter is missing");
    }
    if (StringUtils.isBlank(parameter.get("nameReference"))) {
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
    String[] referenceItems = StringUtils.split(list, ",");
    Set<String> existingSearchKeys = new HashSet<>();

    for (int i = 0; i < referenceItems.length; i++) {
      String trimmedName = StringUtils.trim(referenceItems[i]);
      String searchKey = generateUniqueSearchKey(trimmedName, existingSearchKeys);
      generateReferenceListRecord(trimmedName, searchKey, reference);
      existingSearchKeys.add(searchKey);
    }
  }

  private String generateUniqueSearchKey(String name, Set<String> existingSearchKeys) {
    String baseKey = StringUtils.left(name, 2).toUpperCase();
    String searchKey = baseKey;
    int index = 2;
    while (existingSearchKeys.contains(searchKey) && index < name.length()) {
      searchKey = StringUtils.left(name, index + 1).toUpperCase();
      index++;
    }
    if (existingSearchKeys.contains(searchKey)) {
      searchKey += index; // Adding a numeric suffix in case of further duplicates
    }
    return searchKey;
  }

  private void generateReferenceListRecord(String name, String searchKey, Reference reference) {
    List newReferenceList = OBProvider.getInstance().get(List.class);
    newReferenceList.setNewOBObject(true);
    newReferenceList.setName(name);
    newReferenceList.setSearchKey(searchKey);
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
