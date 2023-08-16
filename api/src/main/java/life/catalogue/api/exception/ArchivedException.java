package life.catalogue.api.exception;

import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;

/**
 * Exception thrown when a taxon or synonym once existed and was archived.
 */
public class ArchivedException extends NotFoundException{
  public final ArchivedNameUsage usage;

  public ArchivedException(DSID<String> key, ArchivedNameUsage usage) {
    super(key, "Usage " + key + " was deleted and archived in release: " + usage.getLastReleaseKey());
    this.usage = usage;
  }
}
