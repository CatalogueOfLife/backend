package life.catalogue.api.vocab;

/**
 *
 */
public enum DatasetType {
  
  /**
   * A list of names focussing on nomenclature and not dealing with taxonomy.
   */
  NOMENCLATURAL,
  
  /**
   * A taxonomic checklist, a global species database (GSD).
   */
  TAXONOMIC,
  
  /**
   * A dataset representing taxonomic treatments of a single scientific article.
   * Mostly published through Plazi or Pensoft at this stage.
   * Subtype of TAXONOMIC.
   */
  ARTICLE,
  
  /**
   * A list of names uploaded for personal use.
   */
  PERSONAL,
  
  /**
   * A taxonomic checklist focussed on providing OTU identifier backed by sequences,
   * usually mixed with classic Linnean classifications.
   * Subtype of TAXONOMIC.
   */
  OTU,
  
  /**
   * A dataset focussed on a specific theme like invasiveness, keys, etc.
   */
  THEMATIC,
  
  OTHER;
}
