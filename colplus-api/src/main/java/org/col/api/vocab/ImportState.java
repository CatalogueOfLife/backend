package org.col.api.vocab;

/**
 *
 */
public enum ImportState {

  /**
   * Currently running import.
   */
  RUNNING,

  /**
   * Sources have not been changed since last import. Imported stopped.
   */
  UNCHANGED,

  /**
   * Successfully completed the import.
   */
  FINISHED,

  /**
   * Manually aborted import, e.g. system was shut down.
   */
  CANCELED,

  /**
   * Import failed due to errors.
   */

  FAILED

}
