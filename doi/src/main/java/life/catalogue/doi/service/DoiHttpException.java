package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;

import javax.annotation.Nullable;

/**
 * Happening when there are some problems on DataCite side (service responded with an HTTP error).
 */
public class DoiHttpException extends DoiException {
  private final int status;
  private final String body;

  public DoiHttpException(int status) {
    super("HTTP " + status);
    this.status = status;
    this.body = null;
  }

  public DoiHttpException(int status, DOI doi) {
    super(doi, "HTTP " + status);
    this.status = status;
    this.body = null;
  }

  public DoiHttpException(int status, DOI doi, String body) {
    super(doi, "HTTP " + status + ": " + body);
    this.status = status;
    this.body = body;
  }

  public DoiHttpException(int status, String body) {
    super("HTTP " + status + ": " + body);
    this.status = status;
    this.body = body;
  }

  public int getStatus() {
    return status;
  }

  /**
   * The raw response body as sent by DataCite, if there was any.
   * Kept separately from the composed exception message so callers can classify an error on data
   * instead of parsing {@link #getMessage()}.
   */
  @Nullable
  public String getBody() {
    return body;
  }
}
