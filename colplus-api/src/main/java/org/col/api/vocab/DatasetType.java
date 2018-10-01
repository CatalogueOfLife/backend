package org.col.api.vocab;

/**
 *
 */
public enum DatasetType {
  /**
   * A list of names focussing on nomenclature and not dealing with taxonomy.
   */
  NOMENCLATURAL,
  
  /**
   * A taxonomic checklist with global coverage, a global species database (GSD).
   */
  GLOBAL,
  
  /**
   * A regional or national checklist.
   */
  REGIONAL,
  
  /**
   * A list of names uploaded for personal use.
   */
  PERSONAL,
  
  OTHER;
}
