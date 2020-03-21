package life.catalogue.api.vocab;

import org.checkerframework.checker.units.qual.A;

/**
 * Vocabulary for the source of truth for the data of a dataset.
 */
public enum DatasetOrigin {
  
  /**
   * A dataset which is synchronised from an external archive pulled from a URL at a regular interval.
   */
  EXTERNAL,

  /**
   * A dataset which is managed directly inside the Clearinghouse through the taxonomic editor.
   */
  MANAGED,

  /**
   * A previously managed dataset that has been released into an immutable release copy.
   */
  RELEASED,

}
