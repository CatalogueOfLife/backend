package life.catalogue.importer;

import life.catalogue.csv.SourceInvalidException;

public class NormalizationFailedException extends RuntimeException {
  
  public NormalizationFailedException(String message) {
    super(message);
  }
  
  public NormalizationFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public NormalizationFailedException(SourceInvalidException cause) {
    super(cause);
  }

  /**
   * For exceptions when required fields are missing.
   */
  public static class MissingDataException extends NormalizationFailedException {
    
    public MissingDataException(String message) {
      super(message);
    }
  }
  
  /**
   * Failed validation assertions.
   */
  public static class AssertionException extends NormalizationFailedException {
    
    public AssertionException(String message) {
      super(message);
    }
  }
}
