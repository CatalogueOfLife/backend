package life.catalogue.outbox;

import life.catalogue.api.event.DeleteSector;
import life.catalogue.api.event.DoiChange;
import life.catalogue.api.event.DoiListener;
import life.catalogue.api.event.SectorListener;
import life.catalogue.api.vocab.Users;
import life.catalogue.doi.service.DoiConfig;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the DOI and sector delete events an app of the previous version still publishes on the shared event queue
 * into outbox commands, so nothing is lost while both versions run side by side during the deploy that introduces the
 * outbox. This version publishes neither event any more, so the bridge only ever sees the old app's.
 *
 * Remove it together with the DoiChange and DeleteSector events once no app of the old version can be running.
 */
public class LegacyEventBridge implements DoiListener, SectorListener {
  private static final Logger LOG = LoggerFactory.getLogger(LegacyEventBridge.class);
  private final SqlSessionFactory factory;
  private final DoiConfig cfg;

  public LegacyEventBridge(SqlSessionFactory factory, DoiConfig cfg) {
    this.factory = factory;
    this.cfg = cfg;
  }

  @Override
  public void doiChanged(DoiChange event) {
    if (event.getDoi() == null || event.getType() == null) return;
    LOG.info("Bridge {} event for DOI {} from the event queue into the outbox", event.getType(), event.getDoi());
    // DoiChange carries no user
    Outbox.doi(factory, cfg, event.getDoi(), DoiAction.valueOf(event.getType().name()), Users.DB_INIT);
  }

  @Override
  public void sectorDeleted(DeleteSector event) {
    if (event.key == null) return;
    LOG.info("Bridge deletion of sector {} from the event queue into the outbox", event.key);
    try (var session = factory.openSession(true)) {
      Outbox.sectorDelete(session, event.key, event.user);
    }
  }
}
