package com.etendoerp.copilot.devassistant;

import java.util.Map;

import javax.servlet.ServletException;

import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
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

public class Utils {
  private Utils() {
    throw new IllegalStateException("Utility class");
  }

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

  public static Module getModuleByName(String name) {
    OBCriteria<Module> criteria = OBDal.getInstance().createCriteria(Module.class);
    criteria.add(Restrictions.eq(Module.PROPERTY_NAME, name));
    criteria.setMaxResults(1);

    return (Module) criteria.uniqueResult();
  }

  public static Module getModuleByID(String id) {
    OBCriteria<Module> criteria = OBDal.getInstance().createCriteria(Module.class);
    criteria.add(Restrictions.eq(Module.PROPERTY_ID, id));
    criteria.setMaxResults(1);

    return (Module) criteria.uniqueResult();
  }
}