package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

public class AddColumn extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

  private static final String TIMESTAMP_WITHOUT_TIMEZONE = "timestamp without timezone";
  private static final String VARCHAR32 = "character varying(32)";
  private static final String VARCHAR60 = "character varying(60)";
  private static final String CHAR1 = "character(1)";
  public static final String NUMERIC = "numeric";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String tableId = parameter.get("TableID");
    String columnName = parameter.get("ColumnName");
    String columnType = parameter.get("ColumnType");
    String defaultParam = parameter.get("DefaultValue");
    String canBeNull = parameter.get("CanBeNull");

    Table table = OBDal.getInstance().get(Table.class, tableId);

    String dbTableName = table.getDBTableName();
    String prefix = dbTableName.split("_")[0];
    String name = dbTableName.substring(dbTableName.indexOf("_") + 1);

    try {
      addColumn(prefix, name, columnName, columnType, defaultParam, StringUtils.equalsIgnoreCase(canBeNull, "true"));
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  public static void addColumn(
      String prefix,
      String tableName,
      String column,
      String columnType,
      String defaultValue,
      boolean canBeNull  ) throws SQLException {

    Connection conn = OBDal.getInstance().getConnection();

    column = getDefaultName(column).trim().replaceAll(" +", "_");

    String dbType = getDbType(columnType);

    String queryCollate = "COLLATE pg_catalog.\"default\"";
    String defaultState = defaultValue.isEmpty() ? "" : " DEFAULT " + defaultValue;
    String queryConstraint = " ";

    if (StringUtils.equals(dbType, TIMESTAMP_WITHOUT_TIMEZONE) || StringUtils.equals(dbType, NUMERIC)) {
      queryCollate = "";
      canBeNull = false;
    }

    String queryNull = canBeNull ? " " : " NOT NULL";

    if (StringUtils.equals(dbType, CHAR1)) {
      String proposal = prefix + "_" + tableName + "_" + column + "_chk";
      String columnOff = column;
      int offset = 1;
      while ((proposal.length() > MAX_LENGTH) && (offset < 15)) {
        String nameOff = tableName.length() > offset ? tableName.substring(offset) : tableName;
        columnOff = column.length() > offset ? column.substring(offset) : column;
        proposal = prefix + "_" + nameOff + "_" + columnOff + "_chk";
        offset++;
      }
      queryConstraint = String.format(
          ", ADD CONSTRAINT %s CHECK (%s = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))",
          proposal,
          columnOff
      );
    }

    String query = String.format(
        "ALTER TABLE IF EXISTS public.%s_%s " +
            "ADD COLUMN IF NOT EXISTS %s %s %s %s %s %s;",
        prefix, tableName, column, dbType, queryNull, queryCollate, defaultState, queryConstraint
    );

    PreparedStatement statement = conn.prepareStatement(query);
    boolean resultBool = statement.execute();
    logIfDebug("Query executed and return:" + resultBool);
  }

  private static String getDbType(String columnType) {
    Map<String, String> mapping = new HashMap<>();
    mapping.put("Absolute DateTime", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("Absolute Time", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("Amount", NUMERIC);
    mapping.put("Assignment", VARCHAR32);
    mapping.put("Binary", "bytea");
    mapping.put("Button", CHAR1);
    mapping.put("Button List", VARCHAR60);
    mapping.put("Color", VARCHAR60);
    mapping.put("Date", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("DateTime", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("DateTime_From (Date)", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("DateTime_To (Date)", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("General Quantity", NUMERIC);
    mapping.put("ID", VARCHAR32);
    mapping.put("Image", VARCHAR60);
    mapping.put("Image BLOB", VARCHAR32);
    mapping.put("Integer", NUMERIC);
    mapping.put("Link", "character varying(200)");
    mapping.put("List", VARCHAR60);
    mapping.put("Masked String", VARCHAR60);
    mapping.put("Memo", "character varying(4000)");
    mapping.put("Non Transactional Sequence", VARCHAR60);
    mapping.put("Number", NUMERIC);
    mapping.put("OBKMO_Widget in Form Reference", VARCHAR32);
    mapping.put("OBUISEL_Multi Selector Reference", VARCHAR60);
    mapping.put("OBUISEL_SelectorAsLink Reference", NUMERIC);
    mapping.put("OBUISEL_Selector Reference", VARCHAR60);
    mapping.put("Password (decryptable)", "character varying(255)");
    mapping.put("Password (not decryptable)", "character varying(255)");
    mapping.put("PAttribute", VARCHAR32);
    mapping.put("Price", NUMERIC);
    mapping.put("Product Characteristics", "character varying(2000)");
    mapping.put("Quantity", NUMERIC);
    mapping.put("Rich Text Area", "text");
    mapping.put("RowID", VARCHAR60);
    mapping.put("Search", VARCHAR32);
    mapping.put("Search Vector", VARCHAR60);
    mapping.put("String", "character varying(200)");
    mapping.put("Table", VARCHAR32);
    mapping.put("TableDir", VARCHAR32);
    mapping.put("Text", "text");
    mapping.put("Time", TIMESTAMP_WITHOUT_TIMEZONE);
    mapping.put("Transactional Sequence", VARCHAR60);
    mapping.put("Tree Reference", VARCHAR32);
    mapping.put("Window Reference", VARCHAR60);
    mapping.put("YesNo", CHAR1);

    return mapping.get(columnType);
  }

  private static String getDefaultName(String name) {
    if (StringUtils.isBlank(name)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultColumnName"));
    }
    return name;
  }

}