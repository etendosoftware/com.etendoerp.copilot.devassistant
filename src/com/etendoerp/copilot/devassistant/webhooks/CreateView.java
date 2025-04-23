package com.etendoerp.copilot.devassistant.webhooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;

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
    String moduleID = parameter.get("ModuleID");
    String querySelect = parameter.get("QuerySelect");
    String javaClass = parameter.get("JavaClass");
    String dataAccessLevel = parameter.get("DataAccessLevel");
    String description = parameter.get("Description");
    String helpTable = parameter.get("Help");

    Connection conn = OBDal.getInstance().getConnection();

    try {
      // Step 1: Get the module and prefix
      Object[] moduleAndPrefix = TableRegistrationUtils.getModuleAndPrefix(moduleID);
      Module module = (Module) moduleAndPrefix[0];
      String prefix = (String) moduleAndPrefix[1];

      if (name.startsWith(prefix)) {
        name = StringUtils.removeStart(name, prefix).substring(1);
      }

      String viewDbName = String.format("%s_%s", prefix, name);
      if (!StringUtils.endsWithIgnoreCase(viewDbName, "_v")) {
        viewDbName = viewDbName + "_v";
      }

      // Step 2: Validate the SELECT query
      checkProyections(querySelect, viewDbName);

      // Step 3: Create the view in the database
      String query = String.format(
          "CREATE OR REPLACE VIEW public.%s AS %s",
          viewDbName, querySelect
      );

      PreparedStatement statement = conn.prepareStatement(query);
      boolean resultBool = statement.execute();
      LOG.info("Query executed and returned: {}", resultBool);

      // Step 4: Register the view in Openbravo
      TableRegistrationUtils.alreadyExistTable(viewDbName);
      DataPackage dataPackage = TableRegistrationUtils.getDataPackage(module);
      javaClass = TableRegistrationUtils.determineJavaClassName(name, javaClass);
      Table adTable = TableRegistrationUtils.createAdTable(dataPackage, javaClass, viewDbName, dataAccessLevel, description, helpTable, true);

      // Step 5: Set response
      responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_ViewCreatedSuccessfully"), adTable.getId()));

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private void checkProyections(String querySelect, String viewDbName) {
    if (querySelect.endsWith(";")) {
      querySelect = querySelect.substring(0, querySelect.length() - 1);
    }
    String query = String.format("SELECT * FROM (%s) AS %s LIMIT 0", querySelect, viewDbName);
    Connection conn = OBDal.getInstance().getConnection();
    try {
      PreparedStatement statement = conn.prepareStatement(query);
      statement.execute();
      ResultSet resultSet = statement.getResultSet();

      List<String> columnList = new ArrayList<>();
      int columnCount = resultSet.getMetaData().getColumnCount();
      for (int i = 1; i <= columnCount; i++) {
        String columnName = resultSet.getMetaData().getColumnName(i);
        LOG.info("Column Name: {}", columnName);
        columnList.add(columnName);
      }
      LOG.info("Column List: {}", Arrays.toString(columnList.toArray()));

      if (columnList.stream().noneMatch(col -> StringUtils.equalsIgnoreCase(col, viewDbName + "_id"))) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("COPDEV_ProjectionColumnNotFound"), viewDbName, viewDbName + "_id"));
      }
      List<String> mandatoryColumns = Arrays.asList("ad_client_id", "ad_org_id", "isactive", "created", "createdby",
          "updated", "updatedby");
      List<String> missingCols c= mandatoryColumns.stream()
          .filter(col -> columnList.stream().noneMatch(c -> StringUtils.equalsIgnoreCase(c, col)))
          .collect(Collectors.toList());
      if (!missingCols.isEmpty()) {
        throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ProjectionColumnNotFound"), viewDbName,
            missingCols.toString()));
      }
    } catch (Exception e) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_InvalidQuery"));
    }
  }
}