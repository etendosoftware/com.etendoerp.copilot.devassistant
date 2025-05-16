package com.etendoerp.copilot.devassistant;

import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Utility class containing shared methods for registering tables and views in Etendo's AD_TABLE.
 */
public class TableRegistrationUtils {

  public static final String REGISTER_COLUMNS_PROCESS = "173";


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
    List<DataPackage> dataPackList = module.getDataPackageList();
    if (dataPackList.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDP"), module.getName()));
    }
    return dataPackList.get(0);
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
    Module module = Utils.getModuleByID(moduleID); // Usamos el método de Utils
    List<ModuleDBPrefix> moduleDBPrefixList = module.getModuleDBPrefixList();
    if (moduleDBPrefixList.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModuleNotFound"), moduleID));
    }
    String prefix = StringUtils.lowerCase(moduleDBPrefixList.get(0).getName());
    return new Object[]{ module, prefix };
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
    adTable.setView(isView); // Indicar explícitamente que es una vista
    OBDal.getInstance().save(adTable);
    OBDal.getInstance().flush();

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
   * @param recordId The ID of the record for which the columns need to be registered.
   * @return A string containing the title and message of the process execution result.
   * @throws ServletException If an error occurs during the execution of the process.
   */
  public static String executeRegisterColumns(String recordId) throws ServletException {
    OBError myMessage = Utils.execPInstanceProcess(REGISTER_COLUMNS_PROCESS, recordId);
    return myMessage.getTitle() + " - " + myMessage.getMessage();
  }
}
