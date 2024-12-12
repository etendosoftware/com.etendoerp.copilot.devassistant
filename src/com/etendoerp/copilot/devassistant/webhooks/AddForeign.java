package com.etendoerp.copilot.devassistant.webhooks;

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

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";
  private static final int MAX_LENGTH = 30;
  private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

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
      if (prefix == null || prefix.equalsIgnoreCase("None")) {
        prefix = childTable.split("_")[0];
      }
      prefix = prefix.toLowerCase();
      parentTable = parentTable.toLowerCase();
      childTable = childTable.toLowerCase();

      String parentTableId = parentTable + "_id";
      String parentColumn = parentTableId;
      boolean externalBool = StringUtils.equalsIgnoreCase(external, "true");

      if (childTable.startsWith(prefix + "_")) {
        childTable = childTable.substring(childTable.indexOf("_") + 1);
      }

      if (externalBool || !(parentTable.startsWith(prefix + "_"))) {
        parentColumn = "em_" + parentColumn;
      }

      AddColumn.addColumn(prefix, childTable, parentColumn, "ID", "", false);

      String constraintFk = CreateTable.getConstName(prefix, childTable, parentTable, "fk");
      RegisterColumns.registerColumns(childTable);

      String query = String.format(
            "DO $$\n"
          + "DECLARE\n"
          + "    foreign_key_exists BOOLEAN;\n"
          + "BEGIN\n"
          + "    SELECT EXISTS (\n"
          + "        SELECT 1\n"
          + "        FROM pg_constraint c\n"
          + "        JOIN pg_class t ON c.conrelid = t.oid\n"
          + "        JOIN pg_namespace n ON t.relnamespace = n.oid\n"
          + "        JOIN pg_class r ON c.confrelid = r.oid\n"
          + "        WHERE c.contype = 'f'\n"
          + "        AND t.relname = '%s_%s'\n"
          + "        AND r.relname = '%s'\n"
          + "        AND c.conkey = ARRAY(SELECT attnum\n"
          + "                             FROM pg_attribute\n"
          + "                             WHERE attrelid = t.oid\n"
          + "                             AND attname = '%s')\n"
          + "        AND c.confkey = ARRAY(SELECT attnum\n"
          + "                              FROM pg_attribute\n"
          + "                              WHERE attrelid = r.oid\n"
          + "                              AND attname = '%s')\n"
          + "    ) INTO foreign_key_exists;\n"
          + "\n"
          + "    IF foreign_key_exists THEN\n"
          + "        UPDATE ad_column\n"
          + "        SET isparent = 'Y'\n"
          + "        WHERE name ILIKE '%s'\n"
          + "        AND isupdateable = 'Y';\n"
          + "    ELSE\n"
          + "        UPDATE ad_column\n"
          + "        SET isparent = 'Y'\n"
          + "        WHERE name ILIKE '%s'\n"
          + "        AND isupdateable = 'Y';\n"
          + "\n"
          + "        ALTER TABLE IF EXISTS public.%s_%s\n"
          + "        ADD CONSTRAINT %s FOREIGN KEY (%s)\n"
          + "        REFERENCES public.%s (%s) MATCH SIMPLE\n"
          + "        ON UPDATE NO ACTION\n"
          + "        ON DELETE NO ACTION;\n"
          + "    END IF;\n"
          + "END $$;\n",
          prefix, childTable, parentTable, parentColumn, parentTableId,
          parentColumn, parentColumn, prefix, childTable, constraintFk,
          parentColumn, parentTable, parentTableId);

      PreparedStatement statement = conn.prepareStatement(query);
      boolean resultBool = statement.execute();
      logIfDebug("Query executed and return:" + resultBool);
      responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_ForeignAddedSucc"), childTable));

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
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
