package com.etendoerp.copilot.devassistant.webhooks;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessRunner;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterFieldsWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logIfDebug("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      logIfDebug(String.format("Parameter: %s = %s", entry.getKey(), entry.getValue()));
    }
    try {
      String tabID = parameter.get("WindowTabID");
      Tab tab = OBDal.getInstance().get(Tab.class, tabID);

      if (tab == null) {
        responseVars.put("error", String.format(OBMessageUtils.messageBD("COPDEV_TabNotFound"), tabID));
        return;
      }

      DalConnectionProvider conn = new DalConnectionProvider(false);
      String pinstance = SequenceIdData.getUUID();
      OBContext context = OBContext.getOBContext();
      PInstanceProcessData.insertPInstance(conn, pinstance, "174", tab.getId(), "Y", context.getUser().getId(),
          context.getCurrentClient().getId(), context.getCurrentOrganization().getId());


      VariablesSecureApp vars = new VariablesSecureApp(context.getUser().getId(), context.getCurrentClient().getId(),
          context.getCurrentOrganization().getId(), context.getRole().getId(), context.getLanguage().getLanguage());


      ProcessBundle bundle = ProcessBundle.pinstance(pinstance, vars, conn);
      new ProcessRunner(bundle).execute(conn);
      PInstanceProcessData[] pinstanceData = PInstanceProcessData.select(conn, pinstance);
      OBError myMessage = Utility.getProcessInstanceMessage(conn, vars, pinstanceData);

      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();


      //Refresh tab and mark the fields created to show them in Grid view
      OBDal.getInstance().refresh(tab);
      List<Field> fields = tab.getADFieldList();
      for (Field field : fields) {
        if (field.getColumn().isKeyColumn()) {
          continue;
        }
        field.setShowInGridView(true);
        OBDal.getInstance().save(field);
      }
      OBDal.getInstance().flush();


      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put("error", textResponse);
      }
    } catch (Exception e) {
      log.error("Error executing process", e);
    }


  }

  private void logIfDebug(String txt) {
    if (log.isDebugEnabled()) {
      log.debug(txt);
    }
  }
}