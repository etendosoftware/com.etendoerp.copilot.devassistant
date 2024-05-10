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
}
