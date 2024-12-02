package com.etendoerp.copilot.devassistant.eventhandler;

import javax.enterprise.event.Observes;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.copilot.data.CopilotAppSource;
import com.etendoerp.copilot.devassistant.Utils;
import com.etendoerp.copilot.util.CopilotConstants;


public class PathFileHandler extends EntityPersistenceEventObserver {
  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(CopilotAppSource.ENTITY_NAME) };
  protected Logger logger = Logger.getLogger(PathFileHandler.class);

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    checkAssistantTypeAndFileType(currentAppSource);
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    final CopilotAppSource currentAppSource = (CopilotAppSource) event.getTargetInstance();
    checkAssistantTypeAndFileType(currentAppSource);
  }

  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

  }

  private static void checkAssistantTypeAndFileType(CopilotAppSource currentAppSource) {
    String fileType = currentAppSource.getFile().getType();
    String copilotAppType = currentAppSource.getEtcopApp().getAppType();

    Utils.validateAppAndFileType(copilotAppType, fileType);
  }
}
