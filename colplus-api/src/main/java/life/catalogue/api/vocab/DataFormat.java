package life.catalogue.api.vocab;

/**
 * Various external data formats supported by the application
 */
public enum DataFormat {
  
  /**
   * Darwin Core Archive
   */
  DWCA,
  
  /**
   * CoL Data Submission Format ("Annual Checklist Exchange Format")
   * http://www.catalogueoflife.org/content/contributing-your-data#ACEF
   */
  ACEF,
  
  /**
   * TDWG Taxonomic Concept Schema
   */
  TCS,
  
  /**
   * COL Data Package
   * See https://github.com/CoL-Data/package-specs
   */
  COLDP,
  
  /**
   * YAML based distributed archive descriptor that proxies remote data files.
   * See https://github.com/Sp2000/colplus-backend/issues/518
   */
  PROXY
  
}
