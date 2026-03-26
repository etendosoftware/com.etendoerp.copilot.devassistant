/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at  
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.copilot.devassistant.webhooks;

import static com.etendoerp.copilot.devassistant.Utils.logExecutionInit;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.ui.Tab;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook to set the SQL WHERE clause (and optional HQL filter) on an AD_TAB.
 * Used to restrict which records are visible in a tab, e.g. "only show products that are courses".
 *
 * <p>Required parameters:
 * <ul>
 *   <li>{@code TabID} — AD_TAB_ID to modify</li>
 *   <li>{@code WhereClause} — SQL WHERE clause, e.g. {@code e.iscourse = 'Y'}</li>
 * </ul>
 *
 * <p>Optional parameters:
 * <ul>
 *   <li>{@code HQLWhereClause} — HQL equivalent, e.g. {@code as e where e.course = true}.
 *       If omitted, only SQLWhereClause is set.</li>
 *   <li>{@code OrderByClause} — SQL ORDER BY clause</li>
 * </ul>
 *
 * <p>Response: {@code {"message": "Filter set on tab '<name>' (ID: <tab_id>)"}}
 */
public class SetTabFilter extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    logExecutionInit(parameter, LOG);

    String tabId = parameter.get("TabID");
    String whereClause = parameter.get("WhereClause");
    String hqlWhereClause = parameter.get("HQLWhereClause");
    String orderByClause = parameter.get("OrderByClause");

    try {
      if (StringUtils.isBlank(tabId)) {
        throw new OBException("TabID parameter is required");
      }
      if (StringUtils.isBlank(whereClause)) {
        throw new OBException("WhereClause parameter is required");
      }

      Tab tab = OBDal.getInstance().get(Tab.class, tabId);
      if (tab == null) {
        throw new OBException(String.format("Tab with ID '%s' not found", tabId));
      }

      OBContext.setAdminMode(true);
      try {
        tab.setSQLWhereClause(whereClause);
        if (StringUtils.isNotBlank(hqlWhereClause)) {
          tab.setHqlwhereclause(hqlWhereClause);
        }
        if (StringUtils.isNotBlank(orderByClause)) {
          tab.setSQLOrderByClause(orderByClause);
        }
        OBDal.getInstance().save(tab);
        OBDal.getInstance().flush();

        responseVars.put("message",
            String.format("Filter set on tab '%s' (ID: %s). WhereClause: %s",
                tab.getName(), tab.getId(), whereClause));
      } finally {
        OBContext.restorePreviousMode();
      }

    } catch (Exception e) {
      LOG.error("Error setting tab filter: {}", e.getMessage(), e);
      responseVars.put("error", e.getMessage());
    }
  }
}
