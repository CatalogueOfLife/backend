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
   * A checklist with some taxonomic assertion. Can be global or regional and does not have to be complete.
   */
  TAXONOMIC,

  /**
   * A taxonomic checklist with the primary focus on producing a phylogenetic tree.
   */
  PHYLOGENETIC,

  /**
   * A dataset representing taxonomic treatments of a single scientific article.
   * Mostly published through Plazi or Pensoft at this stage.
   * Subtype of TAXONOMIC.
   */
  ARTICLE,
  
  /**
   * A checklist which is part of some legal document.
   */
  LEGAL,

  /**
   * A dataset focussed on a specific theme like red lists, invasiveness, traits, species interactions, etc.
   */
  THEMATIC,

  /**
   * A dataset with the focus on being a species identification resource, e.g. keys.
   */
  IDENTIFICATION,
  
  OTHER;
}
