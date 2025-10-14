package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.execPInstanceProcess;
import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

import com.etendoerp.copilot.devassistant.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.ui.Element;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * This class handles the registration of fields for a specified window tab.
 * It retrieves the tab from the database, executes the field registration process,
 * and updates the fields with the provided description and help comments.
 * <p>
 * It extends {@link BaseWebhookService} and overrides the {@link #get(Map, Map)}
 * method to process the webhook request for registering fields.
 */
public class RegisterFields extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  private static final String REGISTER_FIELDS_PROCESS = "174";
  public static final String ERROR_PROPERTY = "error";

  /**
   * Processes the incoming webhook request to register fields for the specified window tab.
   * It retrieves the tab ID, help comment, and description from the parameters,
   * calls the registerFields method to execute the registration process,
   * and updates the fields with the specified help comment and description.
   *
   * @param parameter A map containing the input parameters for the request, including the window tab ID, help comment, and description.
   * @param responseVars A map that will hold the response variables, including the success or error message.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String tabID = parameter.get("WindowTabID");
      String helpComment = parameter.get("HelpComment");
      String description = parameter.get("Description");
      String dbPrefix = parameter.get("DBPrefix");

      Tab tab = OBDal.getInstance().get(Tab.class, tabID);
      Module module = Utils.getModuleByPrefix(dbPrefix);

      if (tab == null) {
        responseVars.put(ERROR_PROPERTY, String.format(OBMessageUtils.messageBD("COPDEV_TabNotFound"), tabID));
        return;
      }
      String recordId = tab.getId();
      OBError myMessage = execPInstanceProcess(REGISTER_FIELDS_PROCESS, recordId);
      String textResponse = myMessage.getTitle() + " - " + myMessage.getMessage();

      // Refresh tab and update fields to show them in Grid view
      OBDal.getInstance().refresh(tab);
      OBDal.getInstance().refresh(module);
      List<Field> fields = tab.getADFieldList();
      fields.stream()
          .filter(field -> !field.getColumn().isKeyColumn())  // Ignore key columns
          .forEach(field -> {

            // Update element names and print text for fields
            Element element = field.getColumn().getApplicationElement();
            String elementName = element.getName();
            String elementPrinTxt = element.getPrintText();
            if (StringUtils.isBlank(elementName)) {
              elementName = field.getColumn().getName();
            }
            if (StringUtils.isBlank(elementPrinTxt)) {
              elementPrinTxt = field.getColumn().getName();
            }
            if (element.getModule() != null && module != null
                    && StringUtils.equals(element.getModule().getId(), module.getId())) {
                element.setName(StringUtils.replace(elementName, "_", " "));
                element.setPrintText(StringUtils.replace(elementPrinTxt, "_", " "));
                OBDal.getInstance().save(element);
            }

            // Set help comment, description, and show field in grid view
            field.setHelpComment(helpComment);
            field.setDescription(description);
            field.setShowInGridView(true);
            if (field.getName() != null) {
              field.setName(StringUtils.replace(field.getName(), "_", " "));
            }
            OBDal.getInstance().save(field);
          });
      OBDal.getInstance().flush();

      if (StringUtils.equalsIgnoreCase(myMessage.getType(), "Success")) {
        responseVars.put("message", textResponse);
      } else {
        responseVars.put(ERROR_PROPERTY, textResponse);
      }
    } catch (Exception e) {
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }
}
