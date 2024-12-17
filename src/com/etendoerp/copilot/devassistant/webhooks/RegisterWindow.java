package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
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

public class RegisterWindow extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";
  public static final String WINDOW_TYPE = "M";
  public static final String MENU_SET_ACTION = "W";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String dbPrefix = parameter.get("DBPrefix");
      String name = parameter.get("Name");
      String description = parameter.get("Description");
      String helpComment = parameter.get("HelpComment");
      String tableId = parameter.get("TableID");

      if (tableId.isEmpty() && dbPrefix.isEmpty()) {
        throw new OBException("Missing parameter, table id and prefix cannot be null.");
      }

      if (dbPrefix.isEmpty()) {
        dbPrefix = OBDal.getInstance().get(Table.class, tableId).getDBTableName().split("_")[0];
      }

      DataPackage dataPackage = getDataPackage(dbPrefix);

      name = fixName(name, dbPrefix);

      OBContext context = OBContext.getOBContext();

      Window window = createWindow(name, dataPackage, context, description, helpComment);

      createMenuElem(context, window, description);

      OBDal.getInstance().flush();

      String copdevWindowCreated = OBMessageUtils.messageBD("COPDEV_WindowCreated");
      responseVars.put("message", String.format(copdevWindowCreated, window.getName(), window.getId()));



    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

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

  private Window createWindow(String name, DataPackage dataPackage, OBContext context, String description, String helpComment) {
    Window window;
    window = OBProvider.getInstance().get(Window.class);
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

  public static String fixName(String name, String dbPrefix) {
    if (name.startsWith(dbPrefix)) {
      name = StringUtils.removeStart(name, dbPrefix).substring(1);
    }
    if (!Character.isUpperCase(name.charAt(0))) {
      name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    if (name.contains("_")) {
      return Arrays.stream(name.replace("_", " ").split(" "))
          .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
          .collect(Collectors.joining(" "));
    }
    return name;
  }

}