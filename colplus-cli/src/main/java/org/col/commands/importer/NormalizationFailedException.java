package org.col.commands.importer;

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
}
