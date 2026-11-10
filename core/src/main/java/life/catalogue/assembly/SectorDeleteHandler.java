package life.catalogue.assembly;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.outbox.Outbox;
import life.catalogue.outbox.OutboxHandler;
import life.catalogue.outbox.RetryLaterException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the sector deletions of the outbox, requested when a sector publisher is removed from a project.
 * A sector that is busy syncing, a stopped job executor or an offline names index retry the deletion later,
 * a sector or project that no longer exists completes it.
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
    boolean queued;
    try {
      queued = syncManager.deleteSector(key, true, entry.getCreatedBy());
    } catch (NotFoundException e) {
      // a missing sector or project, any other illegal argument fails the command and keeps it in sight
      LOG.info("Sector {} cannot be deleted, it is gone already: {}", key, e.getMessage());
      return;
    }
    if (!queued) {
      // a sync was queued since the check above, completing the command would lose the deletion
      throw new RetryLaterException("Deletion of sector " + key + " was rejected, it is busy");
    }
    LOG.info("Queued deletion of sector {} requested by user {}", key, entry.getCreatedBy());
  }
}
