package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class CreateView extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Creating View");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String name = parameter.get("Name");
    String prefix = parameter.get("Prefix");
    String querySelect = parameter.get("QuerySelect");

    Connection conn = OBDal.getInstance().getConnection();

    try {
      if (name.startsWith(prefix)) {
        name = StringUtils.removeStart(name, prefix).substring(1);
      }

      String query = String.format(
          "CREATE OR REPLACE VIEW public.%s_%s_v" +
          " AS %s",
          prefix, name, querySelect
      );

      PreparedStatement statement = conn.prepareStatement(query);
      boolean resultBool = statement.execute();
      logIfDebug("Query executed and return:" + resultBool);
      responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_ViewCreatedSuccessfully"),
          prefix + "_" + name + "_v"));

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

}
