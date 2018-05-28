package org.col.api.vocab;

/**
 *
 */
public enum ImportState {

  /**
   * Downloading the latest source data, the first step of a running import.
   */
  DOWNLOADING,

  /**
   * Normalization of the dataset without touching the previous data in Postgres.
   */
  PROCESSING,

  /**
   * Inserting data into Postgres, starts by wiping any previous edition.
   */
  INSERTING,

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
