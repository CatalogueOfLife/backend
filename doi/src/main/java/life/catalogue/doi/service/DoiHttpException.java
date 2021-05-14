package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;

/**
 * Happening when there are some problems on DataCite side (service responded with an HTTP error).
 */
public class DoiHttpException extends DoiException {
  private final int status;

  public DoiHttpException(int status) {
    super("HTTP " + status);
    this.status = status;
  }

  public DoiHttpException(int status, DOI doi) {
    super(doi, "HTTP " + status);
    this.status = status;
  }

  public DoiHttpException(int status, DOI doi, String message) {
    super(doi, "HTTP " + status + ": " + message);
    this.status = status;
  }

  public DoiHttpException(int status, String message) {
    super("HTTP " + status + ": " + message);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
