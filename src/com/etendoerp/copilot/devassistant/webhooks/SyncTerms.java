package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.execPInstanceProcess;
import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Element;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * This class handles the synchronization of terms and performs actions like executing a synchronization process
 * and cleaning up terms for modules in development.
 */
public class SyncTerms extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final String PROCESS_SYNC_TERM = "172";

  /**
   * Executes the synchronization process for terms and performs clean-up if necessary.
   * <p>
   * The method initializes the process of syncing terms by executing a process with the ID
   * specified by the {@link #PROCESS_SYNC_TERM}. If the "CleanTerms" parameter is set to true,
   * it will clean up specific terms by updating the names and print texts of elements in the modules
   * that are in development.
   * </p>
   *
   * @param parameter A map of input parameters where each key is a parameter name and value is the parameter value.
   * @param responseVars A map where response data is stored. It contains a success message or error message.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      // Initialize process to sync terms
      String recordId = "0";
      OBError myMessage = execPInstanceProcess(PROCESS_SYNC_TERM, recordId);
      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();

      // Handle success or failure message
      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put("error", textResponse);
      }

      // If CleanTerms parameter is true, perform clean-up
      if (!(parameter.get("CleanTerms") != null && StringUtils.equalsIgnoreCase(parameter.get("CleanTerms"), "true"))) {
        return;
      }

      // Clean-up process for elements
      OBCriteria<Module> modCrit = OBDal.getInstance().createCriteria(Module.class);
      modCrit.add(Restrictions.eq(Module.PROPERTY_INDEVELOPMENT, true));
      List<Module> modInDevList = modCrit.list();

      // Search for elements to clean
      OBCriteria<Element> elemCrit = OBDal.getInstance().createCriteria(Element.class);
      elemCrit.add(Restrictions.in(Element.PROPERTY_MODULE, modInDevList));
      elemCrit.add(Restrictions.or(Restrictions.eq(Element.PROPERTY_NAME, Element.PROPERTY_DBCOLUMNNAME),
          Restrictions.eq(Element.PROPERTY_DBCOLUMNNAME, Element.PROPERTY_PRINTTEXT)));
      List<Element> elemList = elemCrit.list();

      // Clean-up element names and print texts
      for (Element element : elemList) {
        element.setName(StringUtils.replaceChars(StringUtils.capitalize(element.getName()), "_", " "));
        element.setPrintText(StringUtils.replaceChars(StringUtils.capitalize(element.getPrintText()), "_", " "));
        OBDal.getInstance().save(element);
      }
      OBDal.getInstance().flush();

    } catch (Exception e) {
      log.error("Error executing process", e);
      responseVars.put("error", e.getMessage());
    }
  }
}
