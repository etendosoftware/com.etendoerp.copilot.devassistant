package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.util.OpenAIUtils.logIfDebug;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class CreateTable extends BaseWebhookService {

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

    String name = parameter.get("Name");
    String prefix = parameter.get("Prefix");

    Connection conn = OBDal.getInstance().getConnection();

    try {
      name = getDefaultName(name);

      if (name.startsWith(prefix)) {
        name = StringUtils.removeStart(name, prefix).substring(1);
      }

      String constraintIsactive = getConstName(prefix, name, "isactive", "chk");
      String constraintPk = getConstName(prefix, name, "", "pk");
      String constraintFkClient = getConstName(prefix, name, "ad_client", "fk");
      String constraintFkOrg = getConstName(prefix, name, "ad_org", "fk");

      String query = String.format(
          "CREATE TABLE IF NOT EXISTS public.%s_%s " +
              "( " +
              "    %s_%s_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
              "    ad_client_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
              "    ad_org_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
              "    isactive character(1) COLLATE pg_catalog.\"default\" NOT NULL DEFAULT 'Y'::bpchar, " +
              "    created timestamp without time zone NOT NULL DEFAULT now(), " +
              "    createdby character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
              "    updated timestamp without time zone NOT NULL DEFAULT now(), " +
              "    updatedby character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
              "    CONSTRAINT %s PRIMARY KEY (%s_%s_id), " +
              "    CONSTRAINT %s FOREIGN KEY (ad_client_id) " +
              "        REFERENCES public.ad_client (ad_client_id) MATCH SIMPLE " +
              "        ON UPDATE NO ACTION " +
              "        ON DELETE NO ACTION, " +
              "    CONSTRAINT %s FOREIGN KEY (ad_org_id) " +
              "        REFERENCES public.ad_org (ad_org_id) MATCH SIMPLE " +
              "        ON UPDATE NO ACTION " +
              "        ON DELETE NO ACTION, " +
              "    CONSTRAINT %s CHECK (isactive = ANY (ARRAY['Y'::bpchar,'N'::bpchar])) " +
              ") " +
              "TABLESPACE pg_default;",
          prefix, name,
          prefix, name,
          constraintPk,
          prefix, name,
          constraintFkClient,
          constraintFkOrg,
          constraintIsactive
      );
      // Replace to public.{prefix}_{name}
      // Replace to {prefix}_{name}_id
      // Replace to {constr_pk}
      // Replace to {prefix}_{name}_id
      // Replace to {constr_fk_client}
      // Replace to {constr_fk_org}
      // Replace to {const_isactive}

      PreparedStatement statement = conn.prepareStatement(query);
      boolean resultBool = statement.execute();
      logIfDebug("Query executed and return:" + resultBool);
      responseVars.put(MESSAGE, String.format(OBMessageUtils.messageBD("COPDEV_TableCreationSucc"), name));

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private String getDefaultName(String name) {
    if (StringUtils.isBlank(name)) {
        return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
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
