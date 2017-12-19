package org.col.api.vocab;

/**
 *
 */
public enum ImportState {

  /**
   * Import still running and should not be picked up by some other thread.
   */
  RUNNING,

  /**
   * Successfully completed the import.
   */
  SUCCESS,

  /**
   * Manually aborted import, e.g. system was shut down.
   */
  ABORTED,

  /**
   * Import failed due to errors while downloading source files.
   */
  FAILED_DOWNLOAD,

  /**
   * Import failed due to errors while normalizing data in neo4j.
   */
  FAILED_NORMALIZER,

  /**
   * Import failed due to errors while importing neo4j into postgres.
   */
  FAILED_PGIMPORT,

  /**
   * Import failed due to errors while building import metrics.
   */
  FAILED_METRICS

}
