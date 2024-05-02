package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.StringUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class ExecProcess extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      log.info("Parameter: " + entry.getKey() + " = " + entry.getValue());
    }

    String processID = parameter.get("processID");
    log.info("Process ID: " + processID);
    if (StringUtils.equalsIgnoreCase(processID, "syncOpenAIAssistant")) {
      log.info("Syncing OpenAI Assistant");
    } else {
      log.info("Unknown process ID");
    }


    responseVars.put("message", "Hola!");
  }
}