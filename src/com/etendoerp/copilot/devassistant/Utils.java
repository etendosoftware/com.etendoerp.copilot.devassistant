package com.etendoerp.copilot.devassistant;

import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
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

public class Utils {
  private Utils() {
    throw new IllegalStateException("Utility class");
  }
  public static final String FILE_TYPE_COPDEV_CI = "COPDEV_CI";

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

  public static void logIfDebug(Logger log, String txt) {
    if (log.isDebugEnabled()) {
      log.debug(txt);
    }
  }

  public static void logExecutionInit(Map<String, String> parameter, Logger logger) {
    logIfDebug(logger, "Executing process");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      logIfDebug(logger, String.format("Parameter: %s = %s", entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Retrieves a Module object based on the given prefix.
   *
   * @param prefix the module prefix
   * @return the Module object, or null if not found
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
   * @return the Module entity that matches the given Java package name, or null if no match is found
   */
  public static Module getModuleByJavaPackage(String moduleJavaPackage) {
    OBCriteria<Module> moduleCrit = OBDal.getInstance().createCriteria(Module.class);
    moduleCrit.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, moduleJavaPackage));
    moduleCrit.setMaxResults(1);
    return (Module) moduleCrit.uniqueResult();
  }

  public static final List<String> CONTROL_TYPES = List.of(
      CopilotConstants.APP_TYPE_LANGCHAIN,
      CopilotConstants.APP_TYPE_MULTIMODEL
  );

  public static boolean isControlType(String appType) {
    return CONTROL_TYPES.contains(appType);
  }

  public static boolean isCodeIndexFile(String fileType) {
    return StringUtils.equals(fileType, FILE_TYPE_COPDEV_CI);
  }

  public static void validateAppAndFileType(String appType, String fileType) {
    if (!isControlType(appType) && isCodeIndexFile(fileType)) {
      throw new OBException(OBMessageUtils.messageBD("COPDEV_FileType&AssistantTypeIncompatibility"));
    }
  }

  public static Table getTableByID(String tableId) {
    OBCriteria<Table> tableCrit = OBDal.getInstance().createCriteria(Table.class);
    tableCrit.add(Restrictions.eq(Table.PROPERTY_ID, tableId));
    tableCrit.setMaxResults(1);
    return (Table) tableCrit.uniqueResult();
  }

  public static Tab getTabByID(String tabId) {
    OBCriteria<Tab> tabCrit = OBDal.getInstance().createCriteria(Tab.class);
    tabCrit.add(Restrictions.eq(Tab.PROPERTY_ID, tabId));
    tabCrit.setMaxResults(1);
    return (Tab) tabCrit.uniqueResult();
  }

  public static Window getWindowByID(String windowId) {
    OBCriteria<Window> windowCrit = OBDal.getInstance().createCriteria(Window.class);
    windowCrit.add(Restrictions.eq(Window.PROPERTY_ID, windowId));
    windowCrit.setMaxResults(1);
    return (Window) windowCrit.uniqueResult();
  }
}