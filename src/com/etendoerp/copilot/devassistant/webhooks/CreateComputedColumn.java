package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Date;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to create a computed (virtual) column in AD_COLUMN.
 * The column is not physical (transient) — its value is derived at query time from a SQL expression.
 * Used for read-only derived fields like "primer curso que vence" in a tab.
 *
 * <p>Required parameters:
 * <ul>
 *   <li>{@code TableID} — AD_TABLE_ID of the table to add the column to (or use {@code TableName})</li>
 *   <li>{@code TableName} — DB table name (alternative to TableID)</li>
 *   <li>{@code ColumnName} — DB column name (e.g. {@code smft_first_expiry_course})</li>
 *   <li>{@code Name} — Display name (e.g. "First Expiry Course")</li>
 *   <li>{@code SQLLogic} — SQL expression for the computed value, e.g.
 *       {@code (SELECT p.name FROM m_product p JOIN smft_enrollment e ON ... WHERE ... LIMIT 1)}</li>
 *   <li>{@code ModuleID} — AD_MODULE_ID that owns this column</li>
 * </ul>
 *
 * <p>Optional parameters:
 * <ul>
 *   <li>{@code ReferenceID} — reference type (default {@code "10"} = String/VARCHAR)</li>
 *   <li>{@code Description} — column description</li>
 * </ul>
 *
 * <p>Response: {@code {"message": "Computed column '<name>' created with ID: <column_id>"}}
 */
public class CreateComputedColumn extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String DEFAULT_REFERENCE_ID = "10"; // String

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);

    String tableId = parameter.get("TableID");
    String tableName = parameter.get("TableName");
    String columnName = parameter.get("ColumnName");
    String name = parameter.get("Name");
    String sqlLogic = parameter.get("SQLLogic");
    String moduleId = parameter.get("ModuleID");
    String referenceId = StringUtils.defaultIfEmpty(parameter.get("ReferenceID"), DEFAULT_REFERENCE_ID);
    String description = parameter.get("Description");

    try {
      if (StringUtils.isBlank(columnName)) {
        throw new OBException("ColumnName parameter is required");
      }
      if (StringUtils.isBlank(name)) {
        throw new OBException("Name parameter is required");
      }
      if (StringUtils.isBlank(sqlLogic)) {
        throw new OBException("SQLLogic parameter is required");
      }
      if (StringUtils.isBlank(moduleId)) {
        throw new OBException("ModuleID parameter is required");
      }
      if (StringUtils.isBlank(tableId) && StringUtils.isBlank(tableName)) {
        throw new OBException("Either TableID or TableName parameter is required");
      }

      Table table = resolveTable(tableId, tableName);
      Module module = Utils.getModuleByID(moduleId);
      Reference reference = OBDal.getInstance().get(Reference.class, referenceId);
      if (reference == null) {
        throw new OBException(String.format("Reference with ID '%s' not found", referenceId));
      }

      checkColumnNotExists(table, columnName);

      OBContext.setAdminMode(true);
      try {
        Column col = createComputedColumn(table, module, reference, columnName, name, sqlLogic, description);
        OBDal.getInstance().flush();

        responseVars.put("message",
            String.format("Computed column '%s' created with ID: %s", name, col.getId()));
      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (Exception e) {
      LOG.error("Error creating computed column: {}", e.getMessage(), e);
      responseVars.put("error", e.getMessage());
      OBDal.getInstance().getSession().clear();
    }
  }

  private Table resolveTable(String tableId, String tableName) {
    if (StringUtils.isNotBlank(tableId)) {
      Table table = OBDal.getInstance().get(Table.class, tableId);
      if (table == null) {
        throw new OBException(String.format("Table with ID '%s' not found", tableId));
      }
      return table;
    }
    return Utils.getTableByDBName(tableName);
  }

  private void checkColumnNotExists(Table table, String columnName) {
    OBCriteria<Column> crit = OBDal.getInstance().createCriteria(Column.class);
    crit.add(Restrictions.eq(Column.PROPERTY_TABLE, table));
    crit.add(Restrictions.ilike(Column.PROPERTY_DBCOLUMNNAME, columnName));
    crit.setMaxResults(1);
    if (crit.uniqueResult() != null) {
      throw new OBException(
          String.format("Column '%s' already exists in table '%s'",
              columnName, table.getDBTableName()));
    }
  }

  private Column createComputedColumn(Table table, Module module, Reference reference,
      String columnName, String name, String sqlLogic, String description) {
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");

    Column col = OBProvider.getInstance().get(Column.class);
    col.setNewOBObject(true);
    col.setClient(client);
    col.setOrganization(org);
    col.setActive(true);
    col.setCreationDate(new Date());
    col.setCreatedBy(OBContext.getOBContext().getUser());
    col.setUpdated(new Date());
    col.setUpdatedBy(OBContext.getOBContext().getUser());
    col.setTable(table);
    col.setModule(module);
    col.setDBColumnName(columnName);
    col.setName(name);
    col.setReference(reference);
    col.setDescription(description);
    col.setSqllogic(sqlLogic);
    col.setTransient(true);   // Not stored in DB — computed at query time
    col.setMandatory(false);
    col.setKeyColumn(false);

    OBDal.getInstance().save(col);
    LOG.info("Computed column '{}' created for table '{}' with ID: {}",
        name, table.getDBTableName(), col.getId());
    return col;
  }
}
