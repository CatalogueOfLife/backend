package org.col.importer;

/**
 *
 */
public class NormalizationFailedException extends RuntimeException {
  
  public NormalizationFailedException(String message) {
    super(message);
  }
  
  public NormalizationFailedException(String message, Throwable cause) {
    super(message, cause);
  }
  
  /**
   * For exceptions when the source files are broken or unusable, e.g. mappings miss required terms.
   */
  public static class SourceInvalidException extends NormalizationFailedException {
    
    public SourceInvalidException(String message) {
      super(message);
    }
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
