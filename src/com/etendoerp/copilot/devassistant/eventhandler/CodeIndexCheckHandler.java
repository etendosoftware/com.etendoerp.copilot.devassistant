package com.etendoerp.copilot.devassistant.eventhandler;

import java.util.List;

import javax.enterprise.event.Observes;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotApp;
import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.copilot.util.CopilotConstants;


/**
 * Event handler for code index management in Copilot applications.
 * <p>
 * This class handles events for {@link CopilotApp} entities such as updates, saving, and deletion.
 * It validates if the application type has changed and ensures that applications with an associated
 * code index cannot be modified.
 */
public class CodeIndexCheckHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {ModelProvider.getInstance().getEntity(CopilotApp.ENTITY_NAME) };
  protected Logger logger = Logger.getLogger(CodeIndexCheckHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles the update event for a {@link CopilotApp} entity.
   * <p>
   * If the application type has changed between the previous and current versions,
   * it checks whether the code index needs to be validated.
   *
   * @param event The update event for the entity.
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotApp currentApp = (CopilotApp) event.getTargetInstance();
    String previousType = (String) event.getPreviousState(currentApp.getEntity().getProperty(CopilotApp.PROPERTY_APPTYPE));
    String currentType = (String) event.getCurrentState(currentApp.getEntity().getProperty(CopilotApp.PROPERTY_APPTYPE));

    if (Utils.isControlType(previousType) != Utils.isControlType(currentType)) {
      checkCodeIndexInAssistant(currentApp);
    }
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
  }

  private static void checkCodeIndexInAssistant(CopilotApp currentApp) {
    List<CopilotAppSource> currentAppSourceList = currentApp.getETCOPAppSourceList();
    for (CopilotAppSource currentAppSource : currentAppSourceList) {
      String type = currentAppSource.getFile().getType();
      if (Utils.isCodeIndexFile(type)) {
        throw new OBException(OBMessageUtils.messageBD("COPDEV_NotChangeAvailableWithCodeIndex"));
      }
    }
  }
}