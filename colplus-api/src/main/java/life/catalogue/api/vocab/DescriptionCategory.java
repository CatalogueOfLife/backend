package life.catalogue.api.vocab;

/**
 * Category of supported CoL descriptions.
 * Leans on Aphia note types.
 */
public enum DescriptionCategory {
  
  /**
   * Any narrative about the biology of the organism.
   * Includes behavior, reproduction, lifecycle, lifespan, dispersal
   */
  BIOLOGY,
  
  /**
   * Any narrative about the conservation status and efforts of the organism.
   */
  CONSERVATION,
  
  /**
   *
   */
  HABITAT,
  
  /**
   * Economic use of the organism.
   */
  USE,
  
  /**
   * Geographic range
   */
  DISTRIBUTION,

  /**
   * Any narrative about descriptive characters of the organism.
   * Includes morphology, lifeform, cytology, size, weigth, genetics, molecular characteristics, chemical compounds, physiology, etc.
   */
  DESCRIPTION,
  
  ETYMOLOGY,
  
  /**
   * A full treatment article ideally marked up as html.
   */
  TREATMENT,
  
  MISCELLANEOUS,
  NOMENCLATURE,
  
  STRATIGRAPHY,
  
  /**
   * Additional taxonomic remarks on a taxon.
   * It can include information on synonymy, new combinations, classification and identification.
   */
  TAXONOMY,
  
  /**
   * Anything on the typification of a name. It's type species, type material,
   * type locality, type designations and their history.
   */
  TYPIFICATION,
  OTHER
}
