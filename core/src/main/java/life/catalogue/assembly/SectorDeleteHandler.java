package life.catalogue.assembly;

import life.catalogue.api.model.OutboxEntry;
import life.catalogue.outbox.Outbox;
import life.catalogue.outbox.OutboxHandler;
import life.catalogue.outbox.RetryLaterException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the sector deletions of the outbox, requested when a sector publisher is removed from a project.
 * A sector that is busy syncing, a stopped job executor or an offline names index retry the deletion later,
 * a sector that no longer exists completes it.
 */
public class SectorDeleteHandler implements OutboxHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDeleteHandler.class);
  private final SyncManager syncManager;

  public SectorDeleteHandler(SyncManager syncManager) {
    this.syncManager = syncManager;
  }

  @Override
  public void handle(OutboxEntry entry) {
    var key = Outbox.parseSectorKey(entry.getKey());
    // checked first as a rejected deletion is recorded as a failed sync attempt of the sector
    if (syncManager.isQueuedOrRunning(key)) {
      throw new RetryLaterException("Sector " + key + " is busy");
    }
    try {
      if (syncManager.deleteSector(key, true, entry.getCreatedBy())) {
        LOG.info("Queued deletion of sector {} requested by user {}", key, entry.getCreatedBy());
      } else {
        LOG.warn("Deletion of sector {} requested by user {} was rejected", key, entry.getCreatedBy());
      }
    } catch (IllegalArgumentException e) {
      LOG.info("Sector {} cannot be deleted, it is gone already: {}", key, e.getMessage());
    }
  }
}
