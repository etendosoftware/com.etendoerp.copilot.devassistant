package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * This class handles the registration of columns for a specified table.
 * It retrieves the table from the database and executes a process to register its columns.
 * <p>
 * It extends {@link BaseWebhookService} and overrides the {@link #get(Map, Map)}
 * method to process the webhook request for registering columns.
 */
public class RegisterColumns extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String REGISTER_COLUMNS_PROCESS = "173";
  public static final String ERROR_PROPERTY = "error";

  /**
   * Processes the incoming webhook request to register columns for the specified table.
   * It retrieves the table name from the parameters, calls the {@link #registerColumns(String)}
   * method to execute the registration process, and adds the response message to the response map.
   *
   * @param parameter A map containing the input parameters for the request, including the table name.
   * @param responseVars A map that will hold the response variables, including the success or error message.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String tableName = parameter.get("TableName");
      responseVars.put("message", registerColumns(tableName));
    } catch (Exception e) {
      log.error("Error executing process", e);
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  /**
   * Registers the columns for the specified table by executing a process on the table.
   * It retrieves the table from the database using the provided table name, and if found,
   * it executes the registration process.
   *
   * @param tableName The name of the table for which columns need to be registered.
   * @return A string message indicating the result of the registration process,
   *         or an error message if the table was not found.
   * @throws ServletException If an error occurs during the process execution.
   */
  static String registerColumns(String tableName) throws ServletException {
    OBCriteria<Table> tableCriteria = OBDal.getInstance().createCriteria(Table.class);
    tableCriteria.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, tableName));
    Table table = (Table) tableCriteria.setMaxResults(1).uniqueResult();
    if (table == null) {
      return String.format(OBMessageUtils.messageBD("COPDEV_TableNotFound"), tableName);
    }
    String recordId = table.getId();
    OBError myMessage = Utils.execPInstanceProcess(REGISTER_COLUMNS_PROCESS, recordId);
    return myMessage.getTitle() + " - " + myMessage.getMessage();
  }
}
