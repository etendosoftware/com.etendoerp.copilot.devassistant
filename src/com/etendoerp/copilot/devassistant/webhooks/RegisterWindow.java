package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * The RegisterWindow class is a webhook service responsible for registering new windows
 * and creating associated menu elements in the Etendo ERP system.
 *
 * This class extends BaseWebhookService and overrides the get method to process parameters
 * and create the necessary records in the database, including windows and menu entries.
 */
public class RegisterWindow extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";
  public static final String WINDOW_TYPE = "M";
  public static final String MENU_SET_ACTION = "W";

  /**
   * Processes the incoming parameters and registers a new window along with its associated menu.
   *
   * @param parameter The map of request parameters, typically containing window and table information.
   * @param responseVars A map for storing the response variables, including error messages or success information.
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      // Extracting parameters from the request
      String dbPrefix = parameter.get("DBPrefix");
      String name = parameter.get("Name");
      String description = parameter.get("Description");
      String helpComment = parameter.get("HelpComment");

      // Validating required parameters
      if ( StringUtils.isEmpty(dbPrefix)) {
        throw new OBException("Missing parameter, prefix cannot be null.");
      }



      // Retrieve the associated DataPackage
      DataPackage dataPackage = getDataPackage(dbPrefix);

      // Normalize the name based on the DB prefix
      name = fixName(name, dbPrefix);

      OBContext context = OBContext.getOBContext();

      // Create the window record
      Window window = createWindow(name, dataPackage, context, description, helpComment);

      // Create the menu entry for the window
      createMenuElem(context, window, description);

      // Commit the changes to the database
      OBDal.getInstance().flush();

      // Prepare the success message
      String copdevWindowCreated = OBMessageUtils.messageBD("COPDEV_WindowCreated");
      responseVars.put("message", String.format(copdevWindowCreated, window.getName(), window.getId()));

    } catch (Exception e) {
      // Rollback in case of error and send error response
      OBDal.getInstance().rollbackAndClose();
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  /**
   * Creates a new menu entry for the given window.
   *
   * @param context The current OBContext containing the organization and client.
   * @param window The window for which the menu is being created.
   * @param description The description of the menu.
   * @return The created Menu object.
   */
  private Menu createMenuElem(OBContext context, Window window, String description) {
    Menu menu = OBProvider.getInstance().get(Menu.class);
    menu.setNewOBObject(true);
    menu.setOrganization(context.getCurrentOrganization());
    menu.setClient(context.getCurrentClient());
    menu.setName(window.getName());
    menu.setWindow(window);
    menu.setSummaryLevel(false);
    menu.setActive(true);
    menu.setAction(MENU_SET_ACTION);
    menu.setOpenlinkinbrowser(false);
    menu.setModule(window.getModule());
    menu.setDescription(description);
    OBDal.getInstance().save(menu);
    return menu;
  }

  /**
   * Creates a new window with the provided parameters and saves it in the database.
   *
   * @param name The name of the window.
   * @param dataPackage The associated data package for the window.
   * @param context The current OBContext containing the organization and client.
   * @param description The description of the window.
   * @param helpComment The help comment for the window.
   * @return The created Window object.
   */
  private Window createWindow(String name, DataPackage dataPackage, OBContext context, String description, String helpComment) {
    Window window = OBProvider.getInstance().get(Window.class);
    window.setNewOBObject(true);
    window.setClient(context.getCurrentClient());
    window.setOrganization(context.getCurrentOrganization());
    window.setName(name);
    window.setModule(dataPackage.getModule());
    window.setWindowType(WINDOW_TYPE);
    window.setSalesTransaction(true);
    window.setDescription(description);
    window.setHelpComment(helpComment);
    OBDal.getInstance().save(window);
    return window;
  }

  /**
   * Retrieves the DataPackage associated with the given DB prefix.
   *
   * @param dbPrefix The database prefix used to find the DataPackage.
   * @return The associated DataPackage.
   * @throws OBException If the prefix is not found or the module is not in development.
   */
  private DataPackage getDataPackage(String dbPrefix) {
    OBCriteria<ModuleDBPrefix> modPrefCrit = OBDal.getInstance().createCriteria(ModuleDBPrefix.class);
    modPrefCrit.add(Restrictions.ilike(ModuleDBPrefix.PROPERTY_NAME, dbPrefix));
    modPrefCrit.setMaxResults(1);
    ModuleDBPrefix modPref = (ModuleDBPrefix) modPrefCrit.uniqueResult();
    if (modPref == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_PrefixNotFound"), dbPrefix));
    }
    Module module = modPref.getModule();
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
   * Fixes the name by removing the DB prefix and formatting it with proper capitalization.
   *
   * @param name The name to be fixed.
   * @param dbPrefix The DB prefix to remove.
   * @return The formatted name.
   */
  public static String fixName(String name, String dbPrefix) {
    if (StringUtils.startsWith(name, dbPrefix)) {
      name = StringUtils.removeStart(name, dbPrefix).substring(1);
    }
    if (!Character.isUpperCase(name.charAt(0))) {
      name = StringUtils.capitalize(name);
    }
    if (StringUtils.contains(name, "_")) {
      return Arrays.stream(name.replace("_", " ").split(" "))
          .map(word -> (StringUtils.isNotBlank(word) ? StringUtils.capitalize(word) : ""))
          .collect(Collectors.joining(" "));
    }
    return name;
  }

}
