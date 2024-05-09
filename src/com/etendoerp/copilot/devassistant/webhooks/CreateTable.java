package com.etendoerp.copilot.devassistant.webhooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class CreateTable extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      log.info("Parameter: " + entry.getKey() + " = " + entry.getValue());
    }

    String mode = parameter.get("mode");
    String query = parameter.get("query");

    try {
      Connection conn = OBDal.getInstance().getConnection();

      PreparedStatement statement = conn.prepareStatement(query);

      log.info(query);

      boolean result = statement.execute();

      log.info("Query executed and return: " + result);

      if (mode.equals("CREATE_TABLE")) {
        responseVars.put("message", "Table created successfully.");
      }
      if (mode.equals("ADD_COLUMN")) {
        responseVars.put("message", "Column added successfully.");
      }

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }
}
