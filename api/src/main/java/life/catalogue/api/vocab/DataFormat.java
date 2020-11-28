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
   * Indented plain text trees for very simple but readable classifications without structured names,
   * but supporting synonyms and basionyms.
   * https://github.com/gbif/text-tree
   */
  TEXT_TREE,
  
  /**
   * COL Data Package
   * See https://github.com/CoL-Data/package-specs
   */
  COLDP,
  
  /**
   * YAML based distributed archive descriptor that proxies individual remote data files into a single archive
   * and allows to semantically map columns and delimiter formats.
   * See https://github.com/Sp2000/colplus-backend/issues/518
   */
  PROXY
  
}
