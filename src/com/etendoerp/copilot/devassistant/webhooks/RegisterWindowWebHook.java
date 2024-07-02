package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.List;
import java.util.Map;

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
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.ad.utility.Tree;
import org.openbravo.model.ad.utility.TreeNode;
import org.quartz.SimpleTrigger;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterWindowWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";
  public static final String windowType = "M";
  public static final String menuSetAction = "W";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String dbPrefix = parameter.get("DBPrefix");
      String name = parameter.get("Name");
      String description = parameter.get("Description");
      String helpComment = parameter.get("HelpComment");

      DataPackage dataPackage = getDataPackage(dbPrefix);

      if (name.startsWith(dbPrefix)) {
        name = name.substring(dbPrefix.length());
      }

      //check that the name has the first letter in uppercase
      if (!Character.isUpperCase(name.charAt(0))) {
        name = Character.toUpperCase(name.charAt(0)) + StringUtils.substring(name, 1);
      }
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

  private void createTreeNode(OBContext context, Menu menu) {
    TreeNode treenode = OBProvider.getInstance().get(TreeNode.class);
    treenode.setNewOBObject(true);
    treenode.setClient(context.getCurrentClient());
    treenode.setOrganization(context.getCurrentOrganization());
    treenode.setNode(menu.getId());
    treenode.setTree(OBDal.getInstance().get(Tree.class, "10"));
    treenode.setReportSet("0");
    treenode.setSequenceNumber((long) 9999);
    OBDal.getInstance().save(treenode);
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
    menu.setAction(menuSetAction);
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
    window.setWindowType(windowType);
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
    if (!module.isInDevelopment()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDev"), module.getName()));
    }
    List<DataPackage> dataPackList = module.getDataPackageList();
    if (dataPackList.isEmpty()) {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_ModNotDP"), module.getName()));
    }
    return dataPackList.get(0);
  }
}