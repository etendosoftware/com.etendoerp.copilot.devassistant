package com.etendoerp.copilot.devassistant.webhooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.copilot.devassistant.TableRegistrationUtils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook service to create a database view in Openbravo.
 * This service creates a view based on a provided SQL query, registers it in the Openbravo application dictionary,
 * and validates that the view meets the required column projections.
 */
public class CreateView extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Creating View");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.debug("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String name = parameter.get("Name");
    String moduleID = parameter.get("ModuleID");
    String querySelect = parameter.get("QuerySelect");
    String javaClass = parameter.get("JavaClass");
    String dataAccessLevel = parameter.get("DataAccessLevel");
    String description = parameter.get("Description");
    String helpTable = parameter.get("Help");

    Connection conn = null;
    PreparedStatement statement = null;

    try {
      // Step 1: Validate required parameters
      if (StringUtils.isEmpty(name)) {
        throw new OBException("Name parameter is required");
      }
      if (StringUtils.isEmpty(moduleID)) {
        throw new OBException("ModuleID parameter is required");
      }
      if (StringUtils.isEmpty(querySelect)) {
        throw new OBException("QuerySelect parameter is required");
      }

      // Step 2: Get the module and prefix
      Object[] moduleAndPrefix = TableRegistrationUtils.getModuleAndPrefix(moduleID);
      Module module = (Module) moduleAndPrefix[0];
      String prefix = (String) moduleAndPrefix[1];
      LOG.debug("Module: {}, Prefix: {}", module.getName(), prefix);

      // Step 3: Construct the view database name
      if (name.startsWith(prefix)) {
        name = StringUtils.removeStart(name, prefix).substring(1);
      }
      String viewDbName = String.format("%s_%s", prefix, name);
      if (!StringUtils.endsWithIgnoreCase(viewDbName, "_v")) {
        viewDbName = viewDbName + "_v";
      }
      LOG.debug("View database name: {}", viewDbName);

      // Step 4: Validate the SELECT query
      checkProyections(querySelect, viewDbName);
      LOG.debug("SELECT query validated successfully");

      // Step 5: Create the view in the database
      conn = OBDal.getInstance().getConnection();
      String query = String.format("CREATE OR REPLACE VIEW public.%s AS %s", viewDbName, querySelect);
      statement = conn.prepareStatement(query);
      boolean resultBool = statement.execute();
      LOG.debug("Query executed and returned: {}", resultBool);

      // Step 6: Commit the transaction to ensure the view is visible
      conn.commit();
      LOG.debug("Transaction committed after creating view");

      // Step 7: Verify that the view was created in the database
      boolean viewExists = false;
      String checkViewQuery = "SELECT 1 FROM pg_views WHERE schemaname = 'public' AND viewname = ?";
      PreparedStatement checkStmt = conn.prepareStatement(checkViewQuery);
      checkStmt.setString(1, viewDbName);
      ResultSet rs = checkStmt.executeQuery();
      if (rs.next()) {
        viewExists = true;
      }
      rs.close();
      checkStmt.close();
      if (!viewExists) {
        throw new OBException("Failed to create view " + viewDbName + " in the database");
      }
      LOG.debug("View {} created successfully in the database", viewDbName);

      // Step 8: Log the columns of the view for debugging
      String columnsQuery = "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?";
      PreparedStatement columnsStmt = conn.prepareStatement(columnsQuery);
      columnsStmt.setString(1, viewDbName);
      ResultSet columnsRs = columnsStmt.executeQuery();
      StringBuilder columnsList = new StringBuilder();
      while (columnsRs.next()) {
        columnsList.append(columnsRs.getString("column_name")).append(", ");
      }
      columnsRs.close();
      columnsStmt.close();
      LOG.debug("Columns of view {}: {}", viewDbName, columnsList.length() > 0 ? columnsList.substring(0, columnsList.length() - 2) : "None");

      // Step 9: Register the view in Openbravo
      OBContext.setAdminMode(true);
      try {
        TableRegistrationUtils.alreadyExistTable(viewDbName);
        DataPackage dataPackage = TableRegistrationUtils.getDataPackage(module);
        javaClass = TableRegistrationUtils.determineJavaClassName(name, javaClass);
        Table adTable = TableRegistrationUtils.createAdTable(dataPackage, javaClass, viewDbName, dataAccessLevel, description, helpTable, true);
        OBDal.getInstance().flush();
        LOG.debug("View registered in AD_TABLE with ID: {}", adTable.getId());

        // Step 10: Verify that columns were registered in AD_COLUMN
        OBCriteria<Column> columnCriteria = OBDal.getInstance().createCriteria(Column.class);
        columnCriteria.add(Restrictions.eq(Column.PROPERTY_TABLE, adTable));
        List<Column> columns = columnCriteria.list();
        if (columns.isEmpty()) {
          throw new OBException("Failed to register columns for view " + viewDbName + " in AD_COLUMN. Possible causes: permission issues, transaction synchronization, or internal error in TableRegistrationUtils.createAdTable.");
        }
        LOG.debug("Registered {} columns for view {}", columns.size(), viewDbName);
        for (Column column : columns) {
          LOG.debug("Column registered: {} (DB Column: {})", column.getName(), column.getDBColumnName());
        }

        // Step 11: Set response
        String messageTemplate = OBMessageUtils.messageBD("COPDEV_ViewCreatedSuccessfully");
        if (messageTemplate == null) {
          messageTemplate = "View %s created successfully";
          LOG.warn("Message 'COPDEV_ViewCreatedSuccessfully' not found in AD_MESSAGE, using default message");
        }
        responseVars.put(MESSAGE, String.format(messageTemplate, adTable.getId()));
      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (SQLException e) {
      LOG.error("SQL error while creating view: {}", e.getMessage(), e);
      responseVars.put("error", "SQL Error: " + e.getMessage());
    } catch (Exception e) {
      LOG.error("Error creating view: {}", e.getMessage(), e);
      responseVars.put("error", e.getMessage());
    } finally {
      try {
        if (statement != null) {
          statement.close();
        }
      } catch (SQLException e) {
        LOG.error("Error closing statement: {}", e.getMessage(), e);
      }
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
        LOG.debug("Column Name: {}", columnName);
        columnList.add(columnName);
      }
      LOG.debug("Column List: {}", Arrays.toString(columnList.toArray()));

      if (columnList.stream().noneMatch(col -> StringUtils.equalsIgnoreCase(col, viewDbName + "_id"))) {
        throw new OBException(
            String.format(OBMessageUtils.messageBD("COPDEV_ProjectionColumnNotFound"), viewDbName, viewDbName + "_id"));
      }
      List<String> mandatoryColumns = Arrays.asList("ad_client_id", "ad_org_id", "isactive", "created", "createdby",
          "updated", "updatedby");
      List<String> missingCols = mandatoryColumns.stream()
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