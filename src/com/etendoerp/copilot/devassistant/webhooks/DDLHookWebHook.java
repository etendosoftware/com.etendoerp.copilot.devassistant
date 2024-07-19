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
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class DDLHookWebHook extends BaseWebhookService {

  private static final String QUERY_EXECUTED = "QueryExecuted";
  private static final String COLUMNS = "Columns";
  private static final String DATA = "Data";

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String mode = parameter.get("Mode");
    String query = parameter.get("Query");
    String name = parameter.get("Name");


    Connection conn = OBDal.getInstance().getConnection();

    try (PreparedStatement statement = conn.prepareStatement(query)) {
      logIfDebug(query);
      boolean resultBool = statement.execute();
      logIfDebug("Query executed and return:" + resultBool);
      name = getDefaultName(mode, name);

      if (StringUtils.equals(mode, DDLToolMode.CREATE_TABLE)) {
        responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_TableCreationSucc"), name));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_COLUMN)) {
        responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_ColumnAddedSucc"), name));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_FOREIGN)) {
        responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_ForeignAddedSucc"), name));
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
          responseVars.put(QUERY_EXECUTED, query);
          responseVars.put(COLUMNS, columns.toString());
          responseVars.put(DATA, data.toString());
      }

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private String getDefaultName(String mode, String name) {
    if (StringUtils.isNotBlank(name)) {
      if (StringUtils.equals(mode, DDLToolMode.CREATE_TABLE)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_COLUMN)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultColumnName"));
      }
    }
    return name;
  }
}
