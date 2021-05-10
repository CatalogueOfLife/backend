package life.catalogue.doi.service;

/**
 * Exception thrown if the used (datacite) metadata was not valid. This can happen if required
 * fields were omitted.
 */
public class InvalidMetadataException extends DoiException {

  public InvalidMetadataException(String message, Throwable e) {
    super(message, e);
  }

  public InvalidMetadataException(String message) {
    super(message);
  }

  public InvalidMetadataException(Throwable e) {
    super("Invalid DataCite metadata", e);
  }
}
