package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.execPInstanceProcess;
import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterFieldsWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final String REGISTER_FIELDS_PROCESS = "174";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String tabID = parameter.get("WindowTabID");
      Tab tab = OBDal.getInstance().get(Tab.class, tabID);

      if (tab == null) {
        responseVars.put("error", String.format(OBMessageUtils.messageBD("COPDEV_TabNotFound"), tabID));
        return;
      }
      String recordId = tab.getId();
      OBError myMessage = execPInstanceProcess(REGISTER_FIELDS_PROCESS, recordId);
      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();
      //Refresh tab and mark the fields created to show them in Grid view
      OBDal.getInstance().refresh(tab);
      List<Field> fields = tab.getADFieldList();
      fields.stream()
          .filter(field -> !field.getColumn().isKeyColumn())
          .forEach(field -> {
            field.setShowInGridView(true);
            OBDal.getInstance().save(field);
          });
      OBDal.getInstance().flush();
      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put("error", textResponse);
      }
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }
}