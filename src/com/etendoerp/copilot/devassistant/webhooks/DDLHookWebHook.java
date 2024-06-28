package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.hibernate.query.Query;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class DDLHookWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final String message = "message";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    log.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      log.info("Parameter: " + entry.getKey() + " = " + entry.getValue());
    }

    String mode = parameter.get("Mode");
    String query = parameter.get("Query");
    String name = parameter.get("Name");
    String element = parameter.get("Element");

    Connection conn = OBDal.getInstance().getConnection();

    try (PreparedStatement statement = conn.prepareStatement(query)) {
      logIfDebug(query);
      boolean resultBool = statement.execute();
      logIfDebug("Query executed and return:" + resultBool);
      name = getDefaultName(mode, name);

      if (StringUtils.equals(mode, DDLToolMode.CREATE_TABLE)) {
        responseVars.put(message, String.format(OBMessageUtils.messageBD("COPDEV_TableCreationSucc"), name));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_COLUMN)) {
        responseVars.put(message, String.format(OBMessageUtils.messageBD("COPDEV_ColumnAddedSucc"), name));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_FOREIGN)) {
        responseVars.put(message, String.format(OBMessageUtils.messageBD("COPDEV_ForeignAddedSucc"), name));
      } else if (StringUtils.equals(mode, DDLToolMode.GET_CONTEXT)) {
          ResultSet result = statement.executeQuery();
          //we will return the result as a JSON object

          //get the columns names
          int columnCount = result.getMetaData().getColumnCount();
          JSONArray columns = new JSONArray();
          for (int i = 1; i <= columnCount; i++) {
            columns.put(result.getMetaData().getColumnName(i));
          }
          JSONArray data = new JSONArray();
          while (result.next()) {
            JSONArray row = new JSONArray();
            for (int i = 1; i <= columnCount; i++) {
              row.put(result.getString(i));
            }
            data.put(row);
          }
          responseVars.put("QueryExecuted", query);
          responseVars.put("Columns", columns.toString());
          responseVars.put("Data", data.toString());
      }

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private String getDefaultName(String mode, String name) {
    if (name == null) {
      if (StringUtils.equals(mode, DDLToolMode.CREATE_TABLE)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_COLUMN)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultColumnName"));
      }
    }
    return name;
  }
}
