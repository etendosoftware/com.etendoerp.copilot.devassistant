package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class DDLHookWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      log.info("Parameter: " + entry.getKey() + " = " + entry.getValue());
    }

    String mode = parameter.get("mode");
    String query = parameter.get("query");
    String name = parameter.get("Name");
    Connection conn = OBDal.getInstance().getConnection();

    try (PreparedStatement statement = conn.prepareStatement(query)) {
      logIfDebug(query);
      boolean result = statement.execute();
      logIfDebug("Query executed and return:" + result);
      name = getDefaultName(mode, name);

      if (StringUtils.equals(mode, "CREATE_TABLE")) {
        responseVars.put("message", String.format(OBMessageUtils.messageBD("copdev_TableCreationSucc"), name));
      }else if (StringUtils.equals(mode, "ADD_COLUMN")) {
        responseVars.put("message", String.format(OBMessageUtils.messageBD("copdev_ColumnAddedSucc"), name));
      }else if (StringUtils.equals(mode, "ADD_FOREIGN")) {
        responseVars.put("message", String.format(OBMessageUtils.messageBD("copdev_ForeignAddedSucc"), name));
      }

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private String getDefaultName(String mode, String name) {
    if (name == null) {
      if (StringUtils.equals(mode, "CREATE_TABLE")) {
        return "Table Name";
      } else if (StringUtils.equals(mode, "ADD_COLUMN")) {
        return "Column Name";
      }
    }
    return name;
  }
}
