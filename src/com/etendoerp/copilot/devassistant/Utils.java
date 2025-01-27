package com.etendoerp.copilot.devassistant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessRunner;
import org.openbravo.service.db.DalConnectionProvider;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;

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
  private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final Random RANDOM = new Random();

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
        context.getUser().getId(),
        context.getCurrentClient().getId(), context.getCurrentOrganization().getId());
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

  public static Module getModuleByName(String name) {
    if (name == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_NameCannotBeNull"));
    }
    OBCriteria<Module> criteria = OBDal.getInstance().createCriteria(Module.class);
    criteria.add(Restrictions.eq(Module.PROPERTY_NAME, name));
    criteria.setMaxResults(1);

    return (Module) criteria.uniqueResult();
  }

  public static Module getModuleByID(String id) {
    if (id == null) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_IDCannotBeNull"));
    }
    OBCriteria<Module> criteria = OBDal.getInstance().createCriteria(Module.class);
    criteria.add(Restrictions.eq(Module.PROPERTY_ID, id));
    criteria.setMaxResults(1);

    return (Module) criteria.uniqueResult();
  }


  // List of control types
  public static final List<String> CONTROL_TYPES = List.of(
      CopilotConstants.APP_TYPE_LANGCHAIN,
      CopilotConstants.APP_TYPE_MULTIMODEL
  );

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
   * Generates a random string of the specified length consisting of uppercase and lowercase letters.
   *
   * @param length
   *     the length of the random string
   * @return a random string of the specified length
   */
  public static String generateRandomString(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
    }
    return sb.toString();
  }

  /**
   * Executes the given SQL query and logs the result.
   *
   * @param query
   *     the SQL query to execute
   * @throws OBException
   *     if an error occurs during query execution
   */
  public static void executeQuery(String query) {
    Connection conn = OBDal.getInstance().getConnection();
    try (PreparedStatement statement = conn.prepareStatement(query)) {
      logIfDebug(LOG, "Executing query: " + query);
       boolean execution = statement.execute();
      logIfDebug(LOG, "Query executed and result: " + execution);

    } catch (Exception e) {
      logIfDebug(LOG, "Error executing query: " + e.getMessage());
      throw new OBException(OBMessageUtils.messageBD("COPDEV_NotValidQuery") + e.getMessage());
    }
  }

  public static Table getTableByDBName(String name) {
    Table table;
    //tyring to get the table by name, because maybe the name is the name instead of the ID
    OBCriteria<Table> criteria = OBDal.getInstance().createCriteria(Table.class);
    criteria.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, name));
    criteria.setMaxResults(1);
    table = (Table) criteria.uniqueResult();
    return table;
  }


  public static boolean columnExists(Table childTableObj, String columnName) {
    return childTableObj.getADColumnList().stream().anyMatch(column -> column.getName().equals(columnName));
  }
}
