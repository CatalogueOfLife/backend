package org.col.admin.task.importer;

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
}
