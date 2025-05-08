package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
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

      String viewDbName = String.format("%s_%s", prefix, name);
      if (!StringUtils.endsWithIgnoreCase(viewDbName, "_v")) {
        viewDbName = viewDbName + "_v";
      }

      checkProyections(querySelect, viewDbName);

      String query = String.format(
          "CREATE OR REPLACE VIEW public." + viewDbName +
              " AS %s",
          querySelect
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

  private void checkProyections(String querySelect, String viewDbName) {
    //execute the query  with a limit 0 to check if the query is valid
    //if the query ends with ; remove it
    if (querySelect.endsWith(";")) {
      querySelect = querySelect.substring(0, querySelect.length() - 1);
    }
    String query = String.format("SELECT * FROM (%s) AS %s LIMIT 0", querySelect, viewDbName);
    Connection conn = OBDal.getInstance().getConnection();
    try {
      PreparedStatement statement = conn.prepareStatement(query);
      statement.execute();
      ResultSet resultSet = statement.getResultSet();

      //get the columnnames from the resultset
      List<String> columnList = new ArrayList<>();
      int columnCount = resultSet.getMetaData().getColumnCount();
      for (int i = 1; i <= columnCount; i++) {
        String columnName = resultSet.getMetaData().getColumnName(i);
        LOG.info("Column Name: " + columnName);
        columnList.add(columnName);
      }
      LOG.info("Column List: " + Arrays.toString(columnList.toArray()));
      //check that exists the column _id for the view and the mandatory columns
      if (columnList.stream().noneMatch(col -> StringUtils.equalsIgnoreCase(col, viewDbName + "_id"))) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("COPDEV_ProjectionColumnNotFound"), viewDbName, viewDbName + "_id"));
      }
      List<String> mandatoryColums = Arrays.asList("ad_client_id", "ad_org_id", "isactive", "created", "createdby",
          "updated", "updatedby");
      List<String> missingCols = mandatoryColums.stream().filter(
          missingCol -> columnList.stream().noneMatch(col -> StringUtils.equalsIgnoreCase(col, missingCol))).collect(
          Collectors.toList());
      if (!missingCols.isEmpty()) {
        throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ProjectionColumnNotFound"), viewDbName,
            missingCols.toString()));
      }


    } catch (Exception e) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_InvalidQuery"));
    }
  }

}
