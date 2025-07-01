package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * This class handles the registration of a new tab for a specified window in the Etendo ERP system.
 * It checks if a tab already exists for the specified table and window, creates a new tab if none exists,
 * and returns appropriate success or error messages.
 */
public class RegisterTab extends BaseWebhookService {

  /**
   * Constant for the window ID parameter
   */
  private static final String WINDOW_ID = "WindowID";
  /**
   * Constant for the tab level parameter
   */
  private static final String TAB_LEVEL = "TabLevel";
  /**
   * Constant for the description parameter
   */
  private static final String DESCRIPTION = "Description";
  /**
   * Constant for the help comment parameter
   */
  private static final String HELP_COMMENT = "HelpComment";
  /**
   * Constant for the table ID parameter
   */
  private static final String TABLE_NAME = "TableName";
  /**
   * Constant for the sequence number parameter
   */
  private static final String SEQUENCE_NUMBER = "SequenceNumber";
  /**
   * Logger instance for logging execution details
   */
  private static final Logger LOG = LogManager.getLogger();
  /**
   * Constant for the error property key in the response map
   */
  public static final String ERROR_PROPERTY = "error";
  /**
   * Constant for the default UI pattern
   */
  public static final String STD = "STD";

  /**
   * Handles the registration of a new tab for the specified window.
   *
   * @param parameter
   *     a map containing the parameters for the tab registration
   * @param responseVars
   *     a map for storing response variables such as error messages or success messages
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {

    logExecutionInit(parameter, LOG);
    try {

      // Extracting parameters from the input map
      String windowId = parameter.get(WINDOW_ID);
      String tabLevel = parameter.get(TAB_LEVEL);
      String description = parameter.get(DESCRIPTION);
      String helpComment = parameter.get(HELP_COMMENT);
      String tableNae = parameter.get(TABLE_NAME);
      String sequenceNumber = parameter.get(SEQUENCE_NUMBER);

      // Fetching the table based on the provided TableID
      Table table = Utils.getTableByDBName(tableNae);

      // Formatting the table name by replacing underscores with spaces
      String name = table.getName().replace("_", " ");


      Window window = OBDal.getInstance().get(Window.class, windowId);

      List<Tab> tabs = window.getADTabList();
      Tab tab = tabs.stream()
          .filter(t -> t.getTable().getId().equals(table.getId()))
          .findFirst()
          .orElse(null);

      // If the tab exists, return an error message
      if (tab != null) {
        window = tab.getWindow();
        String copdevTabAlreadyExists = OBMessageUtils.messageBD("COPDEV_TabAlreadyExists");
        responseVars.put(ERROR_PROPERTY,
            String.format(copdevTabAlreadyExists, tab.getName(), tab.getId(), window.getName()));
        return;
      }

      // Ensuring the tab name starts with an uppercase letter
      if (!Character.isUpperCase(name.charAt(0))) {
        name = Character.toUpperCase(name.charAt(0)) + StringUtils.substring(name, 1);
      }

      OBContext context = OBContext.getOBContext();

      // Fetching the window and setting the table for the window
      table.setWindow(window);
      OBDal.getInstance().save(table);

      // Creating and saving the new tab
      tab = createTab(window, name, table, context, description, helpComment, tabLevel, sequenceNumber);

      OBDal.getInstance().flush();

      // Returning a success message
      String copdevTabCreated = OBMessageUtils.messageBD("COPDEV_TabCreated");
      responseVars.put("message",
          String.format(copdevTabCreated, tab.getName(), tab.getId(), tab.getTabLevel(), tab.getTable().getName(),
              window.getName()));

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  /**
   * Creates a new tab for the specified window and table with the provided details.
   *
   * @param window
   *     the window to associate with the new tab
   * @param name
   *     the name of the tab
   * @param table
   *     the table to associate with the new tab
   * @param context
   *     the OBContext of the current user
   * @param description
   *     a description for the tab
   * @param helpComment
   *     a help comment for the tab
   * @param tabLevel
   *     the level of the tab (e.g., header or line tab)
   * @param sequenceNumber
   *     the sequence number for ordering the tab
   * @return the newly created Tab object
   */
  private Tab createTab(Window window, String name, Table table, OBContext context, String description,
      String helpComment, String tabLevel, String sequenceNumber) {
    Tab tab;
    // Saving the window instance
    OBDal.getInstance().save(window);

    // Creating a new Tab instance
    tab = OBProvider.getInstance().get(Tab.class);
    tab.setNewOBObject(true);
    tab.setClient(context.getCurrentClient());
    tab.setOrganization(context.getCurrentOrganization());
    tab.setTable(table);
    tab.setWindow(window);
    tab.setUIPattern(STD);
    tab.setSequenceNumber(Long.parseLong(sequenceNumber));
    tab.setModule(window.getModule());
    tab.setDescription(description);
    tab.setHelpComment(helpComment);
    tab.setTabLevel(Long.parseLong(tabLevel));

    // Setting the tab name based on the level
    if (Long.parseLong(tabLevel) == 0) {
      tab.setName(name + " Header");
    } else {
      tab.setName(name);
    }

    // Saving the new Tab instance to the database
    OBDal.getInstance().save(tab);
    return tab;
  }
}
