package life.catalogue.api.exception;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.DSID;

/**
 * HTTP 429 TOO MANY REQUESTS
 */
public class TooManyRequestsException extends RuntimeException {

  public TooManyRequestsException(String message) {
    super(message);
  }
}
