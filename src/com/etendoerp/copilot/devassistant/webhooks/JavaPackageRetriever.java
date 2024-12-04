package com.etendoerp.copilot.devassistant.webhooks;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class JavaPackageRetriever extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String KEY_WORD = "KeyWord";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Executing WebHook: JavaPackageRetriever");
    String keyWord = parameter.get(KEY_WORD);


    if (keyWord == null) {
      responseVars.put("error", "Missing parameters.");
      return;
    }

    OBCriteria<Module> criteria = OBDal.getInstance().createCriteria(Module.class);
    criteria.add(Restrictions.ilike(Module.PROPERTY_NAME, "%" + keyWord + "%"));
    List<Module> resultList = criteria.list();
    StringBuilder javapackageList = new StringBuilder();
    for (Module m : resultList) {
      javapackageList.append(m.getJavaPackage()).append(", ");
    }

    responseVars.put("info", javapackageList.toString());
  }
}
