package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;

/** Any exception happening during requests with agency APIs will be converted to this exception. */
public class DoiException extends Exception {

  public DoiException() {}

  public DoiException(DOI doi) {
    super("Error for DOI " + doi.getDoiName());
  }

  public DoiException(DOI doi, String message) {
    super("Error for DOI " + doi.getDoiName() + ": " + message);
  }

  public DoiException(DOI doi, Throwable cause) {
    super("Error for DOI " + doi.getDoiName(), cause);
  }

  public DoiException(String message) {
    super(message);
  }

  public DoiException(Throwable cause) {
    super(cause);
  }

  public DoiException(String message, Throwable cause) {
    super(message, cause);
  }
}
