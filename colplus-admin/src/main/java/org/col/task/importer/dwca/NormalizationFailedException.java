package org.col.task.importer.dwca;

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
   * For exceptions when the dwca cannot be indexed, e.g. mappings miss required terms.
   */
  public static class DwcaInvalidException extends NormalizationFailedException {

    public DwcaInvalidException(String message) {
      super(message);
    }
  }
}
