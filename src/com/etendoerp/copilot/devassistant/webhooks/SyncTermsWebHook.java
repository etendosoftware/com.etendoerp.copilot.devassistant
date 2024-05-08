package com.etendoerp.copilot.devassistant.webhooks;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Element;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessRunner;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class SyncTermsWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logIfDebug("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      logIfDebug(String.format("Parameter: %s = %s", entry.getKey(), entry.getValue()));
    }
    try {
      DalConnectionProvider conn = new DalConnectionProvider(false);
      String pinstance = SequenceIdData.getUUID();
      OBContext context = OBContext.getOBContext();
      PInstanceProcessData.insertPInstance(conn, pinstance, "172", "0", "Y", context.getUser().getId(),
          context.getCurrentClient().getId(), context.getCurrentOrganization().getId());


      VariablesSecureApp vars = new VariablesSecureApp(context.getUser().getId(), context.getCurrentClient().getId(),
          context.getCurrentOrganization().getId(), context.getRole().getId(), context.getLanguage().getLanguage());


      ProcessBundle bundle = ProcessBundle.pinstance(pinstance, vars, conn);
      new ProcessRunner(bundle).execute(conn);
      PInstanceProcessData[] pinstanceData = PInstanceProcessData.select(conn, pinstance);
      OBError myMessage = Utility.getProcessInstanceMessage(conn, vars, pinstanceData);

      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();
      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put("error", textResponse);

      }
      if(parameter.get("cleanTerms") != null && parameter.get("cleanTerms").equals("true")){
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
          element.setName(element.getName().replace("_", " "));
          element.setPrintText(element.getPrintText().replace("_", " "));
          OBDal.getInstance().save(element);
        }
        OBDal.getInstance().flush();
      }


    } catch (Exception e) {
      log.error("Error executing process", e);
    }


  }

  private void logIfDebug(String txt) {
    if(log.isDebugEnabled()){
      log.debug(txt);
    }
  }
}