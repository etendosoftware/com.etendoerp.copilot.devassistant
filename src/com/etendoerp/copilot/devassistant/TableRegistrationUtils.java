package com.etendoerp.copilot.devassistant;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Utility class containing shared methods for registering tables and views in Etendo's AD_TABLE.
 */
public class TableRegistrationUtils {

  private static final Logger LOG = LogManager.getLogger();
  public static final String REGISTER_COLUMNS_PROCESS = "173";
  private static final String SEARCH_REFERENCE_ID = "30";

  private TableRegistrationUtils() {
    throw new AssertionError("Utility class - do not instantiate");
  }

  /**
   * Retrieves the data package associated with the given module.
   *
   * @param module
   *     The module to look for.
   * @return The data package associated with the module.
   * @throws OBException
   *     if no matching data package is found or if the module is not in development.
   */
  public static DataPackage getDataPackage(Module module) {
    if (Boolean.FALSE.equals(module.isInDevelopment())) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDev"), module.getName()));
    }
    List<DataPackage> dataPackList = getDataPackageList(module);
    if (dataPackList.isEmpty()) {
      // Fix Bug 2: auto-create the package so modules created via SQL don't fail
      return createDataPackage(module);
    }
    return dataPackList.get(0);
  }

  /**
   * Auto-creates a DataPackage for a module that was created without one (e.g., via SQL directly).
   * Fixes Bug 2: CreateAndRegisterTable failing with "Module has not a datapackage".
   *
   * @param module The module to create a package for.
   * @return The newly created DataPackage.
   */
  private static DataPackage createDataPackage(Module module) {
    LOG.info("Module '{}' has no DataPackage, auto-creating one", module.getName());
    Client client = OBDal.getInstance().get(Client.class, "0");
    Organization org = OBDal.getInstance().get(Organization.class, "0");
    String javaPackage = StringUtils.defaultIfEmpty(module.getJavaPackage(), module.getName().toLowerCase().replace(" ", "."));

    DataPackage pkg = OBProvider.getInstance().get(DataPackage.class);
    pkg.setNewOBObject(true);
    pkg.setClient(client);
    pkg.setOrganization(org);
    pkg.setModule(module);
    pkg.setName(module.getName());
    pkg.setDescription(module.getName() + " Package");
    pkg.setJavaPackage(javaPackage);
    pkg.setActive(true);
    OBDal.getInstance().save(pkg);
    OBDal.getInstance().flush();
    LOG.info("DataPackage auto-created for module '{}'", module.getName());
    return pkg;
  }

  /**
   * Retrieves a list of data packages associated with the given module.
   * <p>
   * This method creates a criteria query to fetch all {@link DataPackage} objects
   * that are linked to the specified module.
   * </p>
   *
   * @param module
   *     The {@link Module} for which the data packages are to be retrieved.
   * @return A {@link List} of {@link DataPackage} objects associated with the module.
   */
  public static List<DataPackage> getDataPackageList(Module module) {
    return OBDal.getInstance().createCriteria(DataPackage.class)
        .add(Restrictions.eq(DataPackage.PROPERTY_MODULE, module))
        .list();
  }

  /**
   * Retrieves the module and its prefix based on the given module ID.
   *
   * @param moduleID
   *     The ID of the module.
   * @return An array containing the Module object and its prefix as a String.
   * @throws OBException
   *     if the module is not found or has no prefix.
   */
  public static Object[] getModuleAndPrefix(String moduleID) {
    Module module = Utils.getModuleByID(moduleID);
    List<ModuleDBPrefix> moduleDBPrefixList = getModuleDBPrefixList(module);
    if (moduleDBPrefixList.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModulePrefixNotFound"), moduleID));
    }
    String prefix = StringUtils.lowerCase(moduleDBPrefixList.get(0).getName());
    return new Object[]{ module, prefix };
  }

  /**
   * Retrieves a list of database prefixes associated with the given module.
   * <p>
   * This method creates a criteria query to fetch all {@link ModuleDBPrefix} objects
   * that are linked to the specified module.
   * </p>
   *
   * @param module
   *     The {@link Module} for which the database prefixes are to be retrieved.
   * @return A {@link List} of {@link ModuleDBPrefix} objects associated with the module.
   */
  public static List<ModuleDBPrefix> getModuleDBPrefixList(Module module) {
    return OBDal.getInstance().createCriteria(ModuleDBPrefix.class).add(
        Restrictions.eq(ModuleDBPrefix.PROPERTY_MODULE, module)).list();
  }

  /**
   * Checks if a table or view with the specified name already exists in the system.
   *
   * @param tableName
   *     The name of the table or view to check.
   * @return true if the table or view does not exist; false if it exists.
   * @throws OBException
   *     if a table or view with the specified name already exists.
   */
  public static boolean alreadyExistTable(String tableName) {
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
   * Creates a new table or view in the system with the provided attributes.
   *
   * @param dataPackage
   *     The data package associated with the table or view.
   * @param javaClass
   *     The Java class name for the table or view.
   * @param tableName
   *     The name of the table or view.
   * @param dataAccessLevel
   *     The data access level for the table or view.
   * @param description
   *     The description of the table or view.
   * @param helpTable
   *     Help comment or description for the table or view.
   * @param isView
   *     Indicates if the object is a view.
   * @return The newly created table object.
   */
  public static Table createAdTable(DataPackage dataPackage, String javaClass, String tableName, String dataAccessLevel,
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
      tableName = StringUtils.endsWithIgnoreCase(tableName, "_v") ? tableName : tableName + "_v";
      name = StringUtils.endsWithIgnoreCase(name, "V") ? name : name + "V";
    }
    adTable.setName(name);
    adTable.setDBTableName(tableName);
    adTable.setJavaClassName(javaClass);
    adTable.setDescription(description);
    adTable.setHelpComment(helpTable);
    adTable.setView(isView);
    OBDal.getInstance().save(adTable);
    OBDal.getInstance().flush();

    // Fix Bug 6+7: Register all physical columns (PK + standard) into AD_COLUMN
    try {
      executeRegisterColumns(adTable.getId());
    } catch (Exception e) {
      LOG.warn("Could not auto-register columns for table {}: {}", tableName, e.getMessage());
    }

    // Fix Bug 8: createdby/updatedby must use reference '30' (Search), not '18' (Table)
    fixAuditColumnReferences(adTable);

    return adTable;
  }

  /**
   * Determines the Java class name based on the provided name and optional Java class name.
   *
   * @param name
   *     The base name of the table or view.
   * @param javaClass
   *     The optional Java class name provided in the parameters.
   * @return The final Java class name to use.
   */
  public static String determineJavaClassName(String name, String javaClass) {
    if (StringUtils.isEmpty(javaClass) || StringUtils.equals(javaClass, "null")) {
      StringBuilder formattedName = new StringBuilder();
      String[] words = StringUtils.split(StringUtils.replaceChars(name, "_", " "), " ");
      for (String word : words) {
        if (StringUtils.isNotEmpty(word)) {
          formattedName.append(StringUtils.capitalize(word));
        }
      }
      return formattedName.toString();
    }
    return javaClass;
  }

  /**
   * Executes the process to register columns for a given record ID.
   *
   * @param recordId
   *     The ID of the record for which the columns need to be registered.
   * @return A string containing the title and message of the process execution result.
   * @throws ServletException
   *     If an error occurs during the execution of the process.
   */
  public static String executeRegisterColumns(String recordId) throws ServletException {
    OBError myMessage = Utils.execPInstanceProcess(REGISTER_COLUMNS_PROCESS, recordId);
    return myMessage.getTitle() + " - " + myMessage.getMessage();
  }

  /**
   * Fixes the AD_COLUMN reference for createdby and updatedby columns.
   * These must use reference '30' (Search) instead of '18' (Table) so that
   * Hibernate can resolve the FK to AD_User correctly.
   * Fixes Bug 8.
   *
   * @param adTable The table whose audit columns need to be fixed.
   */
  private static void fixAuditColumnReferences(Table adTable) {
    Reference searchRef = OBDal.getInstance().get(Reference.class, SEARCH_REFERENCE_ID);
    if (searchRef == null) {
      LOG.warn("Search reference '{}' not found, skipping audit column fix", SEARCH_REFERENCE_ID);
      return;
    }
    OBDal.getInstance().refresh(adTable);
    OBCriteria<Column> crit = OBDal.getInstance().createCriteria(Column.class);
    crit.add(Restrictions.eq(Column.PROPERTY_TABLE, adTable));
    crit.add(Restrictions.in(Column.PROPERTY_DBCOLUMNNAME, Arrays.asList("createdby", "updatedby")));
    List<Column> auditCols = crit.list();
    for (Column col : auditCols) {
      col.setReference(searchRef);
      OBDal.getInstance().save(col);
    }
    if (!auditCols.isEmpty()) {
      OBDal.getInstance().flush();
      LOG.info("Fixed reference for createdby/updatedby columns in table {}", adTable.getDBTableName());
    }
  }
}
