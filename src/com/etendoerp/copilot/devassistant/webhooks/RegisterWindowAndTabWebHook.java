package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Menu;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.ad.utility.Tree;
import org.openbravo.model.ad.utility.TreeNode;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterWindowAndTabWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String tableId = parameter.get("TableID");
      String name = parameter.get("Name");
      String forceCreate = parameter.get("ForceCreate");

      Table table = OBDal.getInstance().get(Table.class, tableId);
      if (table == null) {
        responseVars.put(ERROR_PROPERTY, String.format(OBMessageUtils.messageBD("COPDEV_TableNotFound"), tableId));
        return;
      }
      OBCriteria<Tab> tabCrit = OBDal.getInstance().createCriteria(Tab.class);
      tabCrit.add(Restrictions.eq(Tab.PROPERTY_TABLE + ".id", tableId));
      Tab tab = (Tab) tabCrit.setMaxResults(1).uniqueResult();
      Window window;
      if (tab != null && StringUtils.equalsIgnoreCase(forceCreate, "false")) {
        window = tab.getWindow();
        String copdevTabAlreadyExists = OBMessageUtils.messageBD("COPDEV_TabAlreadyExists");
        responseVars.put(ERROR_PROPERTY,
            String.format(copdevTabAlreadyExists, tab.getName(), tab.getId(), window.getName()));
        return;
      }
      //check that the name has the first letter in uppercase
      if (!Character.isUpperCase(name.charAt(0))) {
        name = Character.toUpperCase(name.charAt(0)) + StringUtils.substring(name, 1);
      }
      OBContext context = OBContext.getOBContext();
      window = createWindow(name, table, context);
      table.setWindow(window);
      OBDal.getInstance().save(table);
      tab = createTab(window, name, table, context);

      Menu menu = createMenuElem(context, window);

      createTreeNode(context, menu);
      OBDal.getInstance().flush();

      String copdevTabCreated = OBMessageUtils.messageBD("COPDEV_TabCreated");
      responseVars.put("message", String.format(copdevTabCreated, tab.getName(), tab.getId(), window.getName()));
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

  private Menu createMenuElem(OBContext context, Window window) {
    Menu menu = OBProvider.getInstance().get(Menu.class);
    menu.setNewOBObject(true);
    menu.setOrganization(context.getCurrentOrganization());
    menu.setClient(context.getCurrentClient());
    menu.setName(window.getName());
    menu.setWindow(window);
    menu.setSummaryLevel(false);
    menu.setActive(true);
    menu.setAction("W");
    menu.setOpenlinkinbrowser(false);
    menu.setModule(window.getModule());
    OBDal.getInstance().save(menu);
    return menu;
  }

  private Window createWindow(String name, Table table, OBContext context) {
    Window window;
    window = OBProvider.getInstance().get(Window.class);
    window.setNewOBObject(true);
    window.setClient(context.getCurrentClient());
    window.setOrganization(context.getCurrentOrganization());
    window.setName(name);
    window.setModule(table.getDataPackage().getModule());
    window.setWindowType("M");
    window.setSalesTransaction(true);
    OBDal.getInstance().save(window);
    return window;
  }

  private Tab createTab(Window window, String name, Table table, OBContext context) {
    Tab tab;
    OBDal.getInstance().save(window);
    tab = OBProvider.getInstance().get(Tab.class);
    tab.setNewOBObject(true);
    tab.setClient(context.getCurrentClient());
    tab.setOrganization(context.getCurrentOrganization());
    tab.setName(name + " Header");
    tab.setTable(table);
    tab.setWindow(window);
    tab.setTabLevel((long) 0);
    tab.setUIPattern("STD");
    tab.setSequenceNumber((long) 10);
    tab.setModule(window.getModule());
    OBDal.getInstance().save(tab);
    return tab;
  }
}