package life.catalogue.doi.service;


import life.catalogue.api.model.DOI;

/** Happening when trying to delete or update a non-existing DOI. */
public class DoiNotFoundException extends DoiException {
  private final DOI doi;

  public DoiNotFoundException(Throwable cause, DOI doi) {
    super(cause);
    this.doi = doi;
  }

  public DoiNotFoundException(String message, DOI doi) {
    super(message);
    this.doi = doi;
  }

  public DOI getDoi() {
    return doi;
  }
}
