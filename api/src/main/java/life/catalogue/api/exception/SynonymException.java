package life.catalogue.api.exception;

import life.catalogue.api.model.DSID;

/**
 * Exception thrown when a taxon is expected but the given key refers to a synonym.
 */
public class SynonymException extends NotFoundException{
  public final DSID<String> acceptedKey;

  public SynonymException(DSID<String> key, String acceptedKey) {
    super(key, "Synonym " + key + " is not a taxon. Use it's accepted ID instead: " + acceptedKey);
    this.acceptedKey = DSID.of(key.getDatasetKey(), acceptedKey);
  }
}
