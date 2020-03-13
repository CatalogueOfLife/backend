package life.catalogue.api.vocab;

/**
 * Vocabulary for the source of truth for the data of a dataset.
 */
public enum DatasetOrigin {
  
  /**
   * A dataset which is synchronised from an external archive pulled from a URL at a regular interval.
   */
  EXTERNAL,
  
  /**
   * A dataset which is imported from manually uploaded archives at arbitrary intervals.
   */
  UPLOADED,
  
  /**
   * A dataset which is managed directly inside the Clearinghouse through the taxonomic editor.
   */
  MANAGED;
  
}
