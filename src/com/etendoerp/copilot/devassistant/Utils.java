package com.etendoerp.copilot.devassistant;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessRunner;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.copilot.util.CopilotConstants;


/**
 * Utility class that provides common helper methods for various operations in the Copilot module.
 * This class cannot be instantiated and contains static utility methods for logging, process execution,
 * module retrieval, validation, random string generation, and database queries.
 */
public class Utils {

  // Prevent instantiation of this utility class
  private Utils() {
    throw new IllegalStateException("Utility class");
  }

  private static final Logger LOG = LogManager.getLogger();
  public static final String FILE_TYPE_COPDEV_CI = "COPDEV_CI";


  /**
   * Executes a process instance based on the provided process name and record ID.
   *
   * @param registerColumnsProcess
   *     the process name to execute
   * @param recordId
   *     the record ID associated with the process instance
   * @return OBError object containing the result of the process execution
   * @throws ServletException
   *     if an error occurs during process execution
   */
  public static OBError execPInstanceProcess(String registerColumnsProcess, String recordId) throws ServletException {
    DalConnectionProvider conn = new DalConnectionProvider(false);
    String pinstance = SequenceIdData.getUUID();
    OBContext context = OBContext.getOBContext();
    PInstanceProcessData.insertPInstance(conn, pinstance, registerColumnsProcess, recordId, "Y",
        context.getUser().getId(), context.getCurrentClient().getId(), context.getCurrentOrganization().getId());
    VariablesSecureApp vars = new VariablesSecureApp(context.getUser().getId(), context.getCurrentClient().getId(),
        context.getCurrentOrganization().getId(), context.getRole().getId(), context.getLanguage().getLanguage());
    ProcessBundle bundle = ProcessBundle.pinstance(pinstance, vars, conn);
    new ProcessRunner(bundle).execute(conn);
    PInstanceProcessData[] pinstanceData = PInstanceProcessData.select(conn, pinstance);
    return Utility.getProcessInstanceMessage(conn, vars, pinstanceData);
  }

  /**
   * Logs the provided text if the logger is set to debug level.
   *
   * @param log
   *     the logger instance to use for logging
   * @param txt
   *     the text to log
   */
  public static void logIfDebug(Logger log, String txt) {
    if (log.isDebugEnabled()) {
      log.debug(txt);
    }
  }

  /**
   * Logs the execution of a process, including the parameters.
   *
   * @param parameter
   *     a map of parameters to be logged
   * @param logger
   *     the logger to use for logging
   */
  public static void logExecutionInit(Map<String, String> parameter, Logger logger) {
    logIfDebug(logger, "Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      logIfDebug(logger, String.format("Parameter: %s = %s", entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Retrieves a Module object based on the given module prefix.
   *
   * @param prefix
   *     the module prefix
   * @return the Module object, or null if no module is found for the given prefix
   * @throws OBException
   *     if the module with the given prefix is not found
   */
  public static Module getModuleByPrefix(String prefix) {
    OBCriteria<ModuleDBPrefix> criteria = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    criteria.add(Restrictions.eq(ModuleDBPrefix.PROPERTY_NAME, prefix));
    criteria.setMaxResults(1);

    ModuleDBPrefix dbPrefix = (ModuleDBPrefix) criteria.uniqueResult();
    if (dbPrefix == null) {
      throw new OBException(OBMessageUtils.getI18NMessage("COPDEV_NullModule"));
    }
    return dbPrefix.getModule();
  }

  /**
   * Retrieves a Module entity based on the provided Java package name.
   *
   * @param moduleJavaPackage
   *     the Java package name of the module
   * @return the Module entity matching the provided package name, or null if no match is found
   */
  public static Module getModuleByJavaPackage(String moduleJavaPackage) {
    if (moduleJavaPackage == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_JavaPackageCannotBeNull"));
    }
    OBCriteria<Module> moduleCrit = OBDal.getInstance().createCriteria(Module.class);
    moduleCrit.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, moduleJavaPackage));
    moduleCrit.setMaxResults(1);
    return (Module) moduleCrit.uniqueResult();
  }

  /**
   * Retrieves a Module entity based on its unique identifier (ID).
   * <p>
   * This method queries the database to find a Module entity that matches the provided ID.
   * If the ID is null, an exception is thrown. The query is limited to a single result.
   * </p>
   *
   * @param id
   *     The unique identifier of the Module to retrieve.
   * @return The Module entity matching the provided ID, or null if no match is found.
   * @throws OBException
   *     If the provided ID is null.
   */
  public static Module getModuleByID(String id) {
    if (id == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_IDCannotBeNull"));
    }
    OBCriteria<Module> criteria = OBDal.getInstance().createCriteria(Module.class);
    criteria.add(Restrictions.eq(Module.PROPERTY_ID, id));
    criteria.setMaxResults(1);
    Module module = (Module) criteria.uniqueResult();
    if (module == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModuleNotFound"), id));
    }
    return module;
  }

  // List of control types
  public static final List<String> CONTROL_TYPES = List.of(CopilotConstants.APP_TYPE_LANGCHAIN,
      CopilotConstants.APP_TYPE_MULTIMODEL);

  /**
   * Checks if the given appType is a control type.
   *
   * @param appType
   *     the application type to check
   * @return true if the appType is a control type, false otherwise
   */
  public static boolean isControlType(String appType) {
    return CONTROL_TYPES.contains(appType);
  }

  /**
   * Checks if the given file type is a Code Index file.
   *
   * @param fileType
   *     the file type to check
   * @return true if the file type is a Code Index file, false otherwise
   */
  public static boolean isCodeIndexFile(String fileType) {
    return StringUtils.equals(fileType, FILE_TYPE_COPDEV_CI);
  }

  /**
   * Validates the application type and file type compatibility.
   * Throws an OBException if the appType and fileType are incompatible.
   *
   * @param appType
   *     the application type
   * @param fileType
   *     the file type
   * @throws OBException
   *     if the appType and fileType are incompatible
   */
  public static void validateAppAndFileType(String appType, String fileType) {
    if (!isControlType(appType) && isCodeIndexFile(fileType)) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_FileType&AssistantTypeIncompatibility"));
    }
  }

  /**
   * Executes the given SQL query and logs the result.
   *
   * @param query
   *     the SQL query to execute
   * @return a JSONObject containing the result of the query execution
   * @throws SQLException
   *     if an error occurs during query execution
   */
  public static JSONObject executeQuery(String query) throws SQLException {
    var connProv = new DalConnectionProvider();

    PreparedStatement st = null;
    String errmsg = OBMessageUtils.messageBD("COPDEV_NotValidQuery");
    try {
      st = connProv.getPreparedStatement(query);
      logIfDebug(LOG, "Executing query: " + query);
      boolean execution = st.execute();
      logIfDebug(LOG, "Query executed and result: " + execution);
      SQLWarning warnings = st.getWarnings();
      JSONObject response = new JSONObject().put("warnings", warnings);
      if (execution) {//
        JSONArray rows = new JSONArray();
        var rs = st.getResultSet();
        while (rs.next()) {
          JSONObject row = new JSONObject();
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            String columnName = rs.getMetaData().getColumnName(i);
            Object value = rs.getObject(i);
            row.put(columnName, value);
          }
          rows.put(row);
        }
        response.put("result", rows);
      } else {
        response.put("result", st.getUpdateCount());
      }
      st.close();

      return response;
    } catch (Exception e) {

      logIfDebug(LOG, "Error executing query: " + e.getMessage());
      throw new OBException(String.format(errmsg, query, e.getMessage()));
    } finally {
      connProv.releasePreparedStatement(st);
    }

  }

  /**
   * Retrieves a `Table` object based on its database table name.
   * <p>
   * This method attempts to find a `Table` entity by matching the provided name
   * with the database table name (case-insensitive). If no match is found, it returns null.
   * </p>
   *
   * @param name
   *     The database table name to search for.
   * @return The `Table` object matching the provided database table name, or null if no match is found.
   */
  public static Table getTableByDBName(String name) {
    Table table;
    // Trying to get the table by name, because maybe the name is the name instead of the ID
    OBCriteria<Table> criteria = OBDal.getInstance().createCriteria(Table.class);
    criteria.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, name));
    criteria.setMaxResults(1);
    table = (Table) criteria.uniqueResult();
    return table;
  }


  /**
   * Retrieves the data package associated with the given database prefix.
   *
   * @param module
   *     The module to look for.
   * @return The data package associated with the prefix.
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

}
