package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * The {@code CreateAndRegisterTable} class extends {@link BaseWebhookService} and provides functionality to
 * create a table in a PostgreSQL database and register it in Openbravo's AD_TABLE entity.
 * It combines the functionality of creating the table physically in the database and registering it in the system.
 */
public class CreateAndRegisterTable extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_LENGTH = 30;

  /**
   * Processes the incoming webhook request to create and register a table.
   * It creates the table in the database and then registers it in Openbravo's AD_TABLE entity.
   *
   * @param parameter    A map containing the parameters from the incoming request.
   * @param responseVars A map where the response message or error will be stored.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.info("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String name = parameter.get("Name");
    String prefix = parameter.get("DBPrefix");
    String javaClass = parameter.get("JavaClass");
    String tableName = parameter.get("DBTableName");
    String dataAccessLevel = parameter.get("DataAccessLevel");
    String description = parameter.get("Description");
    String helpTable = parameter.get("Help");
    String isView = parameter.get("IsView");
    boolean isViewB = StringUtils.equalsIgnoreCase(isView, "true");

    try {
      // Normalize inputs
      name = StringUtils.lowerCase(name);
      prefix = StringUtils.lowerCase(prefix);
      tableName = StringUtils.isNotEmpty(tableName) ? StringUtils.lowerCase(tableName) : null;

      // Use default name if necessary
      name = getDefaultName(name);

      // Generate table name if DBTableName is not provided
      if (StringUtils.isEmpty(tableName)) {
        tableName = name;
        if (StringUtils.startsWith(name, prefix)) {
          tableName = StringUtils.removeStart(name, prefix);
          if (StringUtils.isNotEmpty(tableName)) {
            tableName = StringUtils.substring(tableName, 1);
          }
        }
      }

      // Ensure tableName starts with prefix
      if (!StringUtils.startsWithIgnoreCase(tableName, prefix)) {
        tableName = prefix + "_" + tableName;
      }

      // Generate Java class name if not provided
      if (javaClass == null || Objects.equals(javaClass, "null")) {
        javaClass = StringUtils.replaceChars(name, "_", " ");
        String[] words = javaClass.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
          if (!StringUtils.isEmpty(word)) {
            formattedName.append(Character.toUpperCase(word.charAt(0)));
            formattedName.append(word.substring(1));
          }
        }
        javaClass = formattedName.toString();
      }

      // Step 1: Create the table in the database
      createTableInDatabase(prefix, tableName, isViewB);

      // Step 2: Register the table in Openbravo
      alreadyExistTable(tableName);
      DataPackage dataPackage = getDataPackage(prefix);
      Table adTable = createAdTable(dataPackage, javaClass, tableName, dataAccessLevel, description, helpTable, isViewB);

      // Step 3: Set response
      responseVars.put("message",
          String.format(OBMessageUtils.messageBD("COPDEV_TableRegistSucc"), adTable.getId()));

    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
      OBDal.getInstance().getSession().clear();
    }
  }

  /**
   * Creates the table physically in the PostgreSQL database.
   *
   * @param prefix    The prefix for the table name.
   * @param tableName The base name of the table.
   * @param isView    Indicates if the table is a view.
   * @throws Exception If an error occurs during table creation.
   */
  private void createTableInDatabase(String prefix, String tableName, boolean isView) throws Exception {
    String constraintIsactive = getConstName(prefix, tableName, "isactive", "chk");
    String constraintPk = getConstName(prefix, tableName, "", "pk");
    String constraintFkClient = getConstName(prefix, tableName, "ad_client", "fk");
    String constraintFkOrg = getConstName(prefix, tableName, "ad_org", "fk");

    String finalTableName = isView ? tableName + "_v" : tableName;

    String query = String.format(
        "CREATE TABLE IF NOT EXISTS public.%s " +
            "( " +
            "    %s_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
            "    ad_client_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
            "    ad_org_id character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
            "    isactive character(1) COLLATE pg_catalog.\"default\" NOT NULL DEFAULT 'Y'::bpchar, " +
            "    created timestamp without time zone NOT NULL DEFAULT now(), " +
            "    createdby character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
            "    updated timestamp without time zone NOT NULL DEFAULT now(), " +
            "    updatedby character varying(32) COLLATE pg_catalog.\"default\" NOT NULL, " +
            "    CONSTRAINT %s PRIMARY KEY (%s_id), " +
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
        finalTableName,
        finalTableName,
        constraintPk,
        finalTableName,
        constraintFkClient,
        constraintFkOrg,
        constraintIsactive
    );

    JSONObject response = Utils.executeQuery(query);
    LOG.info("Table created in database: {}", response.toString());
  }

  /**
   * Returns the default table name if the provided name is blank.
   *
   * @param name The name of the table.
   * @return The provided table name or a default name if the provided name is blank.
   */
  private String getDefaultName(String name) {
    if (StringUtils.isBlank(name)) {
      return String.format(OBMessageUtils.messageBD("COPDEV_DefaultTableName"));
    }
    return name;
  }

  /**
   * Generates a constraint name based on the provided parameters.
   *
   * @param prefix The prefix to be used for the constraint name.
   * @param name1  The first part of the constraint name.
   * @param name2  The second part of the constraint name.
   * @param suffix The suffix to be used for the constraint name.
   * @return The generated constraint name.
   */
  public static String getConstName(String prefix, String name1, String name2, String suffix) {
    if (StringUtils.startsWith(name1, prefix + "_") || StringUtils.startsWithIgnoreCase(name1, prefix + "_")) {
      name1 = StringUtils.substring(name1, prefix.length() + 1);
    }
    if (StringUtils.startsWith(name2, prefix + "_") || StringUtils.startsWithIgnoreCase(name2, prefix + "_")) {
      name2 = StringUtils.substring(name2, prefix.length() + 1);
    }

    String proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;

    if (proposal.length() > MAX_LENGTH && (StringUtils.contains(name1, "_") || StringUtils.contains(name2, "_"))) {
      name1 = name1.replace("_", "");
      name2 = name2.replace("_", "");
      proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix;
    }

    int offset = 1;
    while (proposal.length() > MAX_LENGTH && offset < 15) {
      String name1Offsetted = name1.length() > offset ? StringUtils.substring(name1, offset) : name1;
      String name2Offsetted = name2.length() > offset ? StringUtils.substring(name2, offset) : name2;
      proposal = prefix + "_" + name1Offsetted + "_" + name2Offsetted + "_" + suffix;
      offset++;
    }

    if (proposal.length() > MAX_LENGTH) {
      int length = MAX_LENGTH - prefix.length() - suffix.length() - 2;
      String randomString = Utils.generateRandomString(length);
      proposal = prefix + "_" + randomString + "_" + suffix;
    }

    proposal = proposal.replace("__", "_");
    return proposal;
  }

  /**
   * Creates a new table in the system with the provided attributes.
   *
   * @param dataPackage    The data package associated with the table.
   * @param javaClass      The Java class name for the table.
   * @param tableName      The name of the table.
   * @param dataAccessLevel The data access level for the table.
   * @param description    The description of the table.
   * @param helpTable      Help comment or description for the table.
   * @param isView         Indicates if the table is a view.
   * @return The newly created table object.
   */
  private Table createAdTable(DataPackage dataPackage, String javaClass, String tableName, String dataAccessLevel,
      String description, String helpTable, boolean isView) {
    String name = tableName;
    Table adTable = OBProvider.getInstance().get(Table.class);
    adTable.setNewOBObject(true);
    Client client = OBDal.getInstance().get(Client.class, "0");
    adTable.setClient(client);
    adTable.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
    adTable.setActive(true);
    adTable.setCreationDate(new Date());
    adTable.setCreatedBy(OBContext.getOBContext().getUser());
    adTable.setUpdated(new Date());
    adTable.setUpdatedBy(OBContext.getOBContext().getUser());
    adTable.setDataAccessLevel(dataAccessLevel != null ? dataAccessLevel : "4"); // Default to "Client/Organization"
    adTable.setDataPackage(dataPackage);
    if (isView) {
      tableName = tableName + "_v";
      name = name + "V";
    }
    adTable.setName(name);
    adTable.setDBTableName(tableName);
    adTable.setJavaClassName(javaClass);
    adTable.setDescription(description);
    adTable.setHelpComment(helpTable);
    OBDal.getInstance().save(adTable);
    OBDal.getInstance().flush();

    return adTable;
  }

  /**
   * Checks if a table with the specified name already exists in the system.
   *
   * @param tableName The name of the table to check.
   * @return true if the table does not exist; false if the table exists.
   * @throws OBException if a table with the specified name already exists.
   */
  private boolean alreadyExistTable(String tableName) {
    OBCriteria<Table> tableNameCrit = OBDal.getInstance().createCriteria(Table.class);
    tableNameCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, tableName));
    tableNameCrit.setMaxResults(1);
    Table tableExist = (Table) tableNameCrit.uniqueResult();

    if (tableExist != null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_TableNameAlreadyUse")));
    }
    return true;
  }

  /**
   * Retrieves the data package associated with the given database prefix.
   *
   * @param dbPrefix The database prefix to look for.
   * @return The data package associated with the prefix.
   * @throws OBException if no matching data package is found or if the module is not in development.
   */
  private DataPackage getDataPackage(String dbPrefix) {
    OBCriteria<ModuleDBPrefix> modPrefCrit = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    modPrefCrit.add(Restrictions.ilike(ModuleDBPrefix.PROPERTY_NAME, dbPrefix));
    modPrefCrit.setMaxResults(1);
    ModuleDBPrefix modPref = (ModuleDBPrefix) modPrefCrit.uniqueResult();
    if (modPref == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_PrefixNotFound"), dbPrefix));
    }
    Module module = modPref.getModule();
    if (Boolean.FALSE.equals(module.isInDevelopment())) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDev"), module.getName()));
    }
    List<DataPackage> dataPackList = module.getDataPackageList();
    if (dataPackList.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDP"), module.getName()));
    }
    return dataPackList.get(0);
  }
}