package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.types.resources.Restrict;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;

import com.etendoerp.webhookevents.services.BaseWebhookService;

public class ElementsHandlerWebHook extends BaseWebhookService {

  private static final Logger log = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, log);
    String tableId = parameter.get("TableID");
    OBCriteria<Table> tableCriteria = OBDal.getInstance().createCriteria(Table.class);
    tableCriteria.add(Restrictions.ilike(Table.PROPERTY_ID, tableId));
    Table table = (Table) tableCriteria.setMaxResults(1).uniqueResult();
    List<Column> columns = table.getADColumnList();

    try {
      List<Column> columnsNoDescription = new ArrayList<>();
      for (Column column : columns) {
        if (column.getDescription() == null) {
          System.out.println(column.getName());
          columnsNoDescription.add(column);
          responseVars.put(column.getName(), column.getId());
        }
      }



      } catch (Exception e) {
      responseVars.put("error", e.getMessage());
    }
  }
}

