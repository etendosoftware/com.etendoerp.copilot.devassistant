package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.types.resources.Restrict;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Element;
import org.openbravo.model.ad.ui.Field;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class ElementsHandlerWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    String mode = parameter.get("mode");
    if (StringUtils.equals(mode, "READ_ELEMENTS")) {
      String tableId = parameter.get("TableID");
      Table table = OBDal.getInstance().get(Table.class, tableId);
      List<Column> columns = table.getADColumnList();

      try {
        for (Column column : columns) {
          if (column.getDescription() == null || column.getHelpComment() == null) {
            responseVars.put(column.getName(), column.getId());
          }
        }
      } catch (Exception e) {
        responseVars.put("error", e.getMessage());
      }
    }
    if (StringUtils.equals(mode, "WRITE_ELEMENTS")) {
      try {
        String columnId = parameter.get("ColumnId");
        String description = parameter.get("Description");
        String helpComment = parameter.get("HelpComment");
        Column column = OBDal.getInstance().get(Column.class, columnId);
        column.setDescription(description);
        column.setHelpComment(helpComment);
        Element element = column.getApplicationElement();
        element.setDescription(description);
        element.setHelpComment(helpComment);

        responseVars.put("message", String.format(OBMessageUtils.messageBD("COPDEV_Help&DescriptionAdded"), column.getName()));

      } catch (Exception e) {
        responseVars.put("error", e.getMessage());
      }
    }
  }
}

