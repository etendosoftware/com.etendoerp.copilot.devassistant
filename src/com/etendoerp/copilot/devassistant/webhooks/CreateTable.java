package com.etendoerp.copilot.devassistant.webhooks;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * The {@code CreateTable} class extends {@link BaseWebhookService} and provides functionality to create a table
 * in a PostgreSQL database. The table name is derived from the provided parameters, and constraints such as
 * primary key, foreign keys, and check constraints are added to the table.

 * This class is part of the webhook service, and the {@code get} method is responsible for executing the
 * table creation process based on the parameters received.
 */
public class CreateTable extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

  /**
   * {@inheritDoc}
   *
   * This method is called to process the incoming parameters and create the table in the database. It retrieves
   * the necessary parameters, generates table constraints, and constructs a {@code CREATE TABLE} SQL query.
   *
   * @param parameter a map containing parameters such as table name, prefix, etc.
   * @param responseVars a map to store the response, including error messages if any exception occurs
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.info("Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String name = parameter.get("Name");
    String prefix = parameter.get("Prefix");

    try {
      name = getDefaultName(name);

      if (StringUtils.startsWith(name, prefix)) {
        name = StringUtils.removeStart(name, prefix);
        if (StringUtils.isNotEmpty(name)) {
          name = StringUtils.substring(name, 1);
        }
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

      Utils.executeQuery(query);

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  /**
   * Returns the default table name if the provided name is blank.
   *
   * @param name the name of the table
   * @return the provided table name or a default name if the provided name is blank
   */
  private String getDefaultName(String name) {
    if (StringUtils.isBlank(name)) {
      return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
    }
    return name;
  }

  /**
   * Generates a constraint name based on the provided parameters. The generated name ensures that the length
   * does not exceed the maximum allowed length and makes adjustments to fit the constraint naming conventions.

   * If the name exceeds the maximum length, the name will be shortened by removing underscores or trimming
   * parts of the name until the length constraint is met.

   * @param prefix the prefix to be used for the constraint name
   * @param name1 the first part of the constraint name
   * @param name2 the second part of the constraint name
   * @param suffix the suffix to be used for the constraint name
   * @return the generated constraint name
   */
  public static String getConstName(String prefix, String name1, String name2, String suffix) {
    // Verify and adjust name1 if it starts with the prefix
    if (StringUtils.startsWith(name1, prefix + "_") || StringUtils.startsWithIgnoreCase(name1, prefix + "_")) {
      name1 = StringUtils.substring(name1, prefix.length() + 1);
    }

    // Verify and adjust name2 if it starts with the prefix
    if (StringUtils.startsWith(name2, prefix + "_") || StringUtils.startsWithIgnoreCase(name2, prefix + "_")) {
      name2 = StringUtils.substring(name2, prefix.length() + 1);
    }

    String proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;

    // Reduce length if necessary and contains underscores
    if (proposal.length() > MAX_LENGTH && (StringUtils.contains(name1, "_") || StringUtils.contains(name2, "_"))) {
      name1 = name1.replace("_", "");
      name2 = name2.replace("_", "");
      proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;
    }

    // Adjust names by trimming initial characters
    int offset = 1;
    while (proposal.length() > MAX_LENGTH && offset < 15) {
      String name1Offsetted = name1.length() > offset ? StringUtils.substring(name1, offset) : name1;
      String name2Offsetted = name2.length() > offset ? StringUtils.substring(name2, offset) : name2;
      proposal = prefix + "_" + name1Offsetted + "_" + name2Offsetted + "_" + suffix;
      offset++;
    }

    // Generate a random name if it is still too long
    if (proposal.length() > MAX_LENGTH) {
      int length = MAX_LENGTH - prefix.length() - suffix.length() - 2;
      String randomString = Utils.generateRandomString(length);
      proposal = prefix + "_" + randomString + "_" + suffix;
    }

    proposal = proposal.replace("__", "_");

    return proposal;
  }

}
