/*package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class AddForeign extends BaseWebhookService {

  private static final String QUERY_EXECUTED = "QueryExecuted";
  private static final String COLUMNS = "Columns";
  private static final String DATA = "Data";

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";
  private static final int MAX_LENGTH = 30;
  private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  public static final String CREATE_TABLE = "CREATE_TABLE";


  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String prefix = parameter.get("Prefix");
    String parentTable = parameter.get("ParentTable");
    String childTable = parameter.get("ChildTable");
    String external = parameter.get("External");

    Connection conn = OBDal.getInstance().getConnection();

    try {
      if (childTable.startsWith(prefix + "_")) {
        childTable = childTable.substring(childTable.indexOf("_") + 1);
      }

      String parentTableId = parentTable + "_id";
      String parentColumn = parentTableId;
      boolean externalBool = StringUtils.equalsIgnoreCase(external, "true");

      if (externalBool || !(parentTable.startsWith(prefix + "_"))) {
        parentColumn = "em_" + parentColumn;
        prefix = "em_" + prefix;
      }









      responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_ForeignAddedSucc"), name));


    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private String getDefaultName(String mode, String name) {
    if (StringUtils.isBlank(name)) {
      if (StringUtils.equals(mode, CREATE_TABLE)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
      } else if (StringUtils.equals(mode, DDLToolMode.ADD_COLUMN)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultColumnName"));
      }
    }
    return name;
  }

  public static String getConstName(String prefix, String name1, String name2, String suffix) {
    // Verify and adjust name1 if it starts with the prefix
    if (name1.startsWith(prefix + "_") || name1.toUpperCase().startsWith((prefix + "_").toUpperCase())) {
      name1 = name1.substring(prefix.length() + 1);
    }

    // Verify and adjust name2 if it starts with the prefix
    if (name2.startsWith(prefix + "_") || name2.toUpperCase().startsWith((prefix + "_").toUpperCase())) {
      name2 = name2.substring(prefix.length() + 1);
    }

    String proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;

    // Reduce length if necessary and contains underscores
    if (proposal.length() > MAX_LENGTH && (name1.contains("_") || name2.contains("_"))) {
      name1 = name1.replace("_", "");
      name2 = name2.replace("_", "");
      proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;
    }

    // Adjust names by trimming initial characters
    int offset = 1;
    while (proposal.length() > MAX_LENGTH && offset < 15) {
      String name1Offsetted = name1.length() > offset ? name1.substring(offset) : name1;
      String name2Offsetted = name2.length() > offset ? name2.substring(offset) : name2;
      proposal = prefix + "_" + name1Offsetted + "_" + name2Offsetted + "_" + suffix;
      offset++;
    }

    // Generate a random name if it is still too long
    if (proposal.length() > MAX_LENGTH) {
      int length = MAX_LENGTH - prefix.length() - suffix.length() - 2;
      String randomString = generateRandomString(length);
      proposal = prefix + "_" + randomString + "_" + suffix;
    }

    proposal = proposal.replace("__", "_");

    return proposal;
  }

  private static String generateRandomString(int length) {
    Random random = new Random();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return sb.toString();
  }


}
*/