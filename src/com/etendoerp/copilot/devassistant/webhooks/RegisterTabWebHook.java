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
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class RegisterTabWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();
  public static final String ERROR_PROPERTY = "error";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    try {
      String windowId = parameter.get("WindowID");
      String tabLevel = parameter.get("TabLevel");
      String description = parameter.get("Description");
      String helpComment = parameter.get("Help/Comment");
      String tableId = parameter.get("TableID");
      String sequenceNumber = parameter.get("SequenceNumber");

      Table table = OBDal.getInstance().get(Table.class, tableId);
      if (table == null) {
        responseVars.put(ERROR_PROPERTY, String.format(OBMessageUtils.messageBD("COPDEV_TableNotFound"), tableId));
        return;
      }
      String name = StringUtils.replace(table.getName(), "_", " ");

      OBCriteria<Tab> tabCrit = OBDal.getInstance().createCriteria(Tab.class);
      tabCrit.add(Restrictions.eq(Tab.PROPERTY_TABLE + ".id", tableId));
      Tab tab = (Tab) tabCrit.setMaxResults(1).uniqueResult();
      Window window;


      if (tab != null) {
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

      window = OBDal.getInstance().get(Window.class, windowId);
      table.setWindow(window);
      OBDal.getInstance().save(table);
      tab = createTab(window, name, table, context, description, helpComment, tabLevel, sequenceNumber);

      OBDal.getInstance().flush();

      String copdevTabCreated = OBMessageUtils.messageBD("COPDEV_TabCreated");
      responseVars.put("message", String.format(copdevTabCreated, tab.getName(), tab.getId(), window.getName()));

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      responseVars.put(ERROR_PROPERTY, e.getMessage());
    }
  }

  private Tab createTab(Window window, String name, Table table, OBContext context, String description, String helpComment, String tabLevel, String sequenceNumber) {
    Tab tab;
    OBDal.getInstance().save(window);
    tab = OBProvider.getInstance().get(Tab.class);
    tab.setNewOBObject(true);
    tab.setClient(context.getCurrentClient());
    tab.setOrganization(context.getCurrentOrganization());
    tab.setTable(table);
    tab.setWindow(window);
    tab.setUIPattern("STD");
    tab.setSequenceNumber(Long.parseLong(sequenceNumber));
    tab.setModule(window.getModule());
    tab.setDescription(description);
    tab.setHelpComment(helpComment);
    tab.setTabLevel(Long.parseLong(tabLevel));
    if (Long.parseLong(tabLevel) == 0) {
      tab.setName(name + " Header");
    } else{
      tab.setName(name);
    }
    OBDal.getInstance().save(tab);
    return tab;
  }
}