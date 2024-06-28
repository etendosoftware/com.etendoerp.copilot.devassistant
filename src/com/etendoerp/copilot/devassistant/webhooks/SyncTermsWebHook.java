package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.execPInstanceProcess;
import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;
import static com.etendoerp.copilot.devassistant.Utils.logIfDebug;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Element;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class SyncTermsWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final String PROCESS_SYNC_TERM = "172";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String recordId = "0";
      OBError myMessage = execPInstanceProcess(PROCESS_SYNC_TERM, recordId);
      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();
      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put("error", textResponse);
      }
      if (!(parameter.get("CleanTerms") != null && StringUtils.equalsIgnoreCase(parameter.get("CleanTerms"), "true"))) {
        return;
      }
      OBCriteria<Module> modCrit = OBDal.getInstance().createCriteria(Module.class);
      modCrit.add(Restrictions.eq(Module.PROPERTY_INDEVELOPMENT, true));
      List<Module> modInDevList = modCrit.list();
      //search for AD_element to clean
      OBCriteria<Element> elemCrit = OBDal.getInstance().createCriteria(Element.class);
      elemCrit.add(Restrictions.in(Element.PROPERTY_MODULE, modInDevList));
      elemCrit.add(Restrictions.or(Restrictions.eq(Element.PROPERTY_NAME, Element.PROPERTY_DBCOLUMNNAME),
          Restrictions.eq(Element.PROPERTY_DBCOLUMNNAME, Element.PROPERTY_PRINTTEXT)));
      List<Element> elemList = elemCrit.list();
      for (Element element : elemList) {
        element.setName(StringUtils.replaceChars(StringUtils.capitalize(element.getName()), "_", " "));
        element.setPrintText(StringUtils.replaceChars(StringUtils.capitalize(element.getPrintText()), "_", " "));
        OBDal.getInstance().save(element);
      }
      OBDal.getInstance().flush();

    } catch (Exception e) {
      log.error("Error executing process", e);
      responseVars.put("error", e.getMessage());
    }
  }
}