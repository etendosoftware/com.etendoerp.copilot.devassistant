package com.etendoerp.copilot.devassistant.webhooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

import kong.unirest.json.JSONArray;

public class GetWindowTabOrTableInfo extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String QUERY_EXECUTED = "QueryExecuted";
  private static final String COLUMNS = "Columns";
  private static final String DATA = "Data";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }
    List<String> allowedKeywords = Arrays.asList("table", "window", "tab");

    String name = parameter.get("Name");
    String keyWord = parameter.get("KeyWord");

    String query = "SELECT ad_" + keyWord + "_id FROM ad_" + keyWord + " WHERE name ilike '%" + name + "%'";

    Connection conn = OBDal.getInstance().getConnection();

    try (PreparedStatement statement = conn.prepareStatement(query)) {
      keyWord = keyWord.toLowerCase();
      if (!allowedKeywords.contains(keyWord)) {
        throw new OBException("Key word is not correct.");
      }

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

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

}
