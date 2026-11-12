package life.catalogue.gbifsync;

import life.catalogue.api.model.OutboxEntry;
import life.catalogue.outbox.Outbox;
import life.catalogue.outbox.OutboxHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the GBIF_REGISTRY outbox commands: brings the GBIF record of a dataset registered in GBIF from ChecklistBank in
 * line with ChecklistBank and asks GBIF to crawl it, or deletes the GBIF record of a deregistered or deleted dataset.
 * Commands of a registration and of a successful import also upload the dataset's EML, for any dataset with a GBIF key.
 */
public class GbifRegistryHandler implements OutboxHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GbifRegistryHandler.class);
  private final GbifRegistrar registrar;

  public GbifRegistryHandler(GbifRegistrar registrar) {
    this.registrar = registrar;
  }

  @Override
  public void handle(OutboxEntry entry) throws Exception {
    registrar.reconcile(Integer.parseInt(entry.getKey()), Outbox.gbifDeregisterKeys(entry.getParams()),
      Outbox.gbifPushMetadata(entry.getParams()));
  }

  /**
   * Online if datasets can be registered or the EML of other datasets uploaded. The commands of registered datasets
   * wait for registering to be configured as well.
   */
  @Override
  public boolean isOnline() {
    return registrar.isEnabled() || registrar.canUploadMetadata();
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
