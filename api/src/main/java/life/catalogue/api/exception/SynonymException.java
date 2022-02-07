package life.catalogue.api.exception;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Taxon;

/**
 * Exception thrown when a taxon is expected but the given key refers to a synonym.
 */
public class SynonymException extends NotFoundException{
  public final DSID<String> acceptedKey;

  public SynonymException(DSID<String> key, String acceptedKey) {
    super(key, NotFoundException.createMessage(Taxon.class, key.concat()));
    this.acceptedKey = DSID.of(key.getDatasetKey(), acceptedKey);
  }
}
