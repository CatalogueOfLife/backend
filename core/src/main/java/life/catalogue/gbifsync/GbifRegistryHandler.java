package life.catalogue.gbifsync;

import life.catalogue.api.model.OutboxEntry;
import life.catalogue.outbox.Outbox;
import life.catalogue.outbox.OutboxHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the GBIF_REGISTRY outbox commands: brings the GBIF record of a dataset registered in GBIF from ChecklistBank in
 * line with ChecklistBank and asks GBIF to crawl it, or deletes the GBIF record of a deregistered or deleted dataset.
 * Commands wait while registering datasets in GBIF is not configured.
 */
public class GbifRegistryHandler implements OutboxHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GbifRegistryHandler.class);
  private final GbifRegistrar registrar;

  public GbifRegistryHandler(GbifRegistrar registrar) {
    this.registrar = registrar;
  }

  @Override
  public void handle(OutboxEntry entry) throws Exception {
    registrar.reconcile(Integer.parseInt(entry.getKey()), Outbox.gbifDeregisterKey(entry.getParams()));
  }

  @Override
  public boolean isOnline() {
    return registrar.isEnabled();
  }

  /**
   * A request GBIF refused cannot succeed by running it again, e.g. bad credentials or a record deleted in GBIF.
   * Fix the cause and retry the command under /admin/outbox.
   */
  @Override
  public boolean isPermanent(Exception e) {
    return e instanceof IllegalArgumentException
      || e instanceof GbifRegistryClient.GbifRegistryException ge && ge.isClientError();
  }

  @Override
  public void onFailed(OutboxEntry entry, Exception e) {
    LOG.error("Failed to update the GBIF registry for dataset {}: {}", entry.getKey(), e.getMessage(), e);
  }
}
