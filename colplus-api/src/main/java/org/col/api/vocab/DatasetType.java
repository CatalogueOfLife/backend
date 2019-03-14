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
   * A dataset representing taxonomic treatments of a single scientific article.
   * Mostly published through Plazi or Pensoft at this stage.
   */
  ARTICLE,
  
  /**
   * A list of names uploaded for personal use.
   */
  PERSONAL,
  
  OTHER;
}
