package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;
import static com.etendoerp.copilot.devassistant.Utils.logIfDebug;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Element;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Class that handles elements names on the Application Dictionary
 */

public class ElementsHandler extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    String mode = parameter.get("Mode");

    if (StringUtils.equals(mode, "READ_ELEMENTS")) {    // Method to read the elements of a table
      readMode(parameter, responseVars);
    } else if (StringUtils.equals(mode, "WRITE_ELEMENTS")) {    // Method to set the Description and HelpComment
      writeMode(parameter, responseVars);
    } else {
      throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_WrongMode")));
    }
  }

  private static void writeMode(Map<String, String> parameter, Map<String, String> responseVars) {
    try {
      String columnId = parameter.get("ColumnID");
      if (columnId == null) {
        throw new OBException(String.format(OBMessageUtils.messageBD("COPDEV_InvalidElementID")));
      }
      String name = parameter.get("Name");
      String description = parameter.get("Description");
      String helpComment = parameter.get("HelpComment");
      Column column = OBDal.getInstance().get(Column.class, columnId);
      if (column == null) {
        throw new IllegalArgumentException("Column with ID " + columnId + " not found.");
      }
      if (!column.getName().startsWith("EM_")) {
        column.setName(StringUtils.replace(column.getName(), "_", " "));
      }
      column.setDescription(description);
      column.setHelpComment(helpComment);
      Element element = column.getApplicationElement();
      if (element == null) {
        throw new IllegalArgumentException("Element not found.");
      }

      String elementName = StringUtils.isBlank(name) ? element.getName() : name;
      String elementPrinTxt = element.getPrintText();
      if (StringUtils.isBlank(elementName)) {
        elementName = column.getName();
      }
      if (StringUtils.isBlank(elementPrinTxt)) {
        elementPrinTxt = column.getName();
      }
      element.setName(StringUtils.replace(elementName, "_", " "));
      element.setPrintText(StringUtils.replace(elementPrinTxt, "_", " "));
      element.setDescription(description);
      element.setHelpComment(helpComment);
      logIfDebug(log, column.getName());
      logIfDebug(log, element.getName());

      responseVars.put("message",
          String.format(OBMessageUtils.messageBD("COPDEV_Help&DescriptionAdded"), column.getName(), element.getName(),
              element.getPrintText()));

      // Ensure changes are persisted immediately
      OBDal.getInstance().flush();
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }

  private static void readMode(Map<String, String> parameter, Map<String, String> responseVars) {
    String tableId = parameter.get("TableID");
    Table table = OBDal.getInstance().get(Table.class, tableId);

    if (table == null) {
      throw new IllegalArgumentException("Table with ID " + tableId + " not found.");
    }

    List<Column> columns = table.getADColumnList();

    try {
      JSONArray array = new JSONArray();
      for (Column column : columns) {

        if (StringUtils.isBlank(column.getDescription()) || StringUtils.isBlank(column.getHelpComment())) {
          String msg = "Column " + column.getName() + "(ID: " + column.getId() + ")";
          if (StringUtils.isBlank(column.getDescription())) {
            msg += " is missing a description.";
          }
          if (StringUtils.isBlank(column.getHelpComment())) {
            msg += " is missing a help comment.";
          }
          array.put(msg);
        }
      }
      responseVars.put("response", array.toString());
    } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }
}
