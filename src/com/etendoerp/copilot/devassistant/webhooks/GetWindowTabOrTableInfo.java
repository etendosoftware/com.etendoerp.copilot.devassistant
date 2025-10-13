package com.etendoerp.copilot.devassistant.webhooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.webhookevents.services.BaseWebhookService;

import kong.unirest.json.JSONArray;

/**
 * This class handles the retrieval of information about a window, table, or tab
 * based on the provided parameters. It executes an SQL query to fetch details from
 * the database and returns the results in a JSON format.

 * It extends {@link BaseWebhookService} and overrides the {@link #get(Map, Map)}
 * method to process the webhook request.
 */
public class GetWindowTabOrTableInfo extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String QUERY_EXECUTED = "QueryExecuted";
  private static final String COLUMNS = "Columns";
  private static final String DATA = "Data";

  /**
   * Processes the incoming webhook request to fetch details about a window, table,
   * or tab based on the specified parameters. It constructs an SQL query based on the
   * provided name and key word, executes the query, and returns the results in JSON format.
   *
   * @param parameter
   *     A map containing the input parameters for the request, including
   *     "Name" and "KeyWord".
   * @param responseVars
   *     A map that will hold the response variables, including the
   *     query executed, column names, and data retrieved.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Getting Information.");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    List<String> allowedKeywords = Arrays.asList("column","field","table", "window", "tab");

    String name = parameter.get("Name");
    String keyWord = parameter.get("KeyWord");

    if (StringUtils.isBlank(keyWord)) {
      responseVars.put("error", "KeyWord parameter is required");
      return;
    }

    keyWord = StringUtils.lowerCase(keyWord.trim());

    // Validate the keyWord to ensure it's allowed
    if (!allowedKeywords.contains(keyWord)) {
      responseVars.put("error", "Key word is not correct.");
      return;
    }

    // Determine parent key column (if any) to include in the SELECT list
    String parentKeyColumn = null; // column name in the underlying AD_ table
    switch (keyWord) {
      case "column":
        parentKeyColumn = "ad_table_id";
        break;
      case "field":
        parentKeyColumn = "ad_tab_id";
        break;
      case "tab":
        parentKeyColumn = "ad_window_id";
        break;
      default: // window, table -> no parent
        break;
    }

    // Build the SELECT clause dynamically
    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("SELECT ad_").append(keyWord).append("_id, ");
    if (StringUtils.equals(keyWord, "table")) {
      queryBuilder.append("tablename, ");
    }
    if (parentKeyColumn != null) {
      queryBuilder.append(parentKeyColumn).append(", ");
    }
    queryBuilder.append("name FROM ad_").append(keyWord)
        .append(" WHERE (name ILIKE ? ");
    boolean hasTableNameSearch = StringUtils.equals(keyWord, "table");
    if (hasTableNameSearch) {
      queryBuilder.append(" OR tablename ILIKE ? ");
    }
    queryBuilder.append(" OR ad_").append(keyWord).append("_id = ?) ");

    String query = queryBuilder.toString();
    LOG.debug("Executing query: {}", query);

    Connection conn = OBDal.getInstance().getConnection();

    try (PreparedStatement statement = conn.prepareStatement(query)) {
      // Bind parameters
      String likeValue = "%" + name + "%";
      int paramIndex = 1;
      statement.setString(paramIndex++, likeValue);
      if (hasTableNameSearch) {
        statement.setString(paramIndex++, likeValue);
      }
      statement.setString(paramIndex, name); // id match (exact)

      ResultSet result = statement.executeQuery();

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

  /**
   * Returns the provided text if the keyWord is "table", otherwise returns an empty string.
   * <p>
   * This method checks if the given keyWord is equal to "table" (case-insensitive).
   * If it is, the method returns the provided text. Otherwise, it returns an empty string.
   *
   * @param keyWord
   *     the keyword to check
   * @param text
   *     the text to return if the keyword is "table"
   * @return the text if the keyword is "table", otherwise an empty string
   */
  private String ifIsTable(String keyWord, String text) {
    if (StringUtils.equalsIgnoreCase(keyWord, "table")) {
      return text;
    }
    return "";
  }

}
