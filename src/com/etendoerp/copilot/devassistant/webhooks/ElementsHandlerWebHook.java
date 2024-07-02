package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;
import static com.etendoerp.copilot.devassistant.Utils.logIfDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.types.resources.Restrict;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Element;
import org.openbravo.model.ad.ui.Field;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Class that handles elements names on the Application Dictionary
 */

public class ElementsHandlerWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    String mode = parameter.get("Mode");

    if (StringUtils.equals(mode, DDLToolMode.READ_ELEMENTS)) {    // Method to read the elements of a table
      read_mode(parameter, responseVars);
    } else if (StringUtils.equals(mode, DDLToolMode.WRITE_ELEMENTS)) {    // Method to set the Description and HelpComment
      write_mode(parameter, responseVars);
    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_WrongMode")));
    }
  }

  private static void write_mode(Map<String, String> parameter, Map<String, String> responseVars) {
    try {
      String columnId = parameter.get("ColumnId");
      if (columnId == null) {
        throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_InvalidElementID")));
      }

      String description = parameter.get("Description");
      String helpComment = parameter.get("HelpComment");
      Column column = OBDal.getInstance().get(Column.class, columnId);
      if (column == null) {
        throw new IllegalArgumentException("Column with ID " + columnId + " not found.");
      }
      column.setName(StringUtils.replace(column.getName(), "_", " "));
      column.setDescription(description);
      column.setHelpComment(helpComment);
      Element element = column.getApplicationElement();
      if (element == null) {
        throw new IllegalArgumentException("Element not found.");
      }
      element.setName(StringUtils.replace(element.getName(), "_", " "));
      element.setPrintText(StringUtils.replace(element.getName(), "_", " "));
      element.setDescription(description);
      element.setHelpComment(helpComment);
      logIfDebug(log,column.getName());
      logIfDebug(log,element.getName());

      responseVars.put("message", String.format(OBMessageUtils.messageBD("COPDEV_Help&DescriptionAdded"), column.getName(), element.getName(), element.getPrintText()));
      OBDal.getInstance().flush();
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private static void read_mode(Map<String, String> parameter, Map<String, String> responseVars) {
    String tableId = parameter.get("TableID");
    Table table = OBDal.getInstance().get(Table.class, tableId);

    if (table == null) {
      throw new IllegalArgumentException("Table with ID " + tableId + " not found.");
    }

    List<Column> columns = table.getADColumnList();

    try {
      for (Column column : columns) {
        if (StringUtils.isBlank(column.getDescription()) || StringUtils.isBlank(column.getHelpComment())) {
          responseVars.put(column.getName(), column.getId());
        }
      }
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }
}

