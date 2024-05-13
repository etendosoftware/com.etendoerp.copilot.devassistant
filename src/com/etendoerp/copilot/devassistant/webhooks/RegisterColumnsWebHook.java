package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterColumnsWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String REGISTER_COLUMNS_PROCESS = "173";
  public static final String ERROR_PROPERTY = "error";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {

      String tableName = parameter.get("tableName");
      OBCriteria<Table> tableCriteria = OBDal.getInstance().createCriteria(Table.class);
      tableCriteria.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, tableName));
      Table table = (Table) tableCriteria.setMaxResults(1).uniqueResult();
      if (table == null) {
        responseVars.put(ERROR_PROPERTY, String.format(OBMessageUtils.messageBD("COPDEV_TableNotFound"), tableName));
        return;
      }
      String recordId = table.getId();
      OBError myMessage = Utils.execPInstanceProcess(REGISTER_COLUMNS_PROCESS, recordId);
      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();
      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put(ERROR_PROPERTY, textResponse);

      }
    } catch (Exception e) {
      log.error("Error executing process", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

}