package life.catalogue.csv;

/**
 * Exception thrown by csv readers if a source archive folder is invalid for some reasons.
 */
public class SourceInvalidException extends IllegalArgumentException {

  public SourceInvalidException(String message) {
    super(message);
  }

}
