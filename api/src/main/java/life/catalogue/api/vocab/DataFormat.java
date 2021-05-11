package life.catalogue.api.vocab;

/**
 * Various external data formats supported by the application
 */
public enum DataFormat {
  
  /**
   * Darwin Core Archive
   */
  DWCA(true, "DwC-A", "Darwin Core Archive"),
  
  /**
   * CoL Data Submission Format ("Annual Checklist Exchange Format")
   * http://www.catalogueoflife.org/content/contributing-your-data#ACEF
   */
  ACEF(true, "ACEF", "Annual Checklist Exchange Format"),
  
  /**
   * Indented plain text trees for very simple but readable classifications without structured names,
   * but supporting synonyms and basionyms.
   * https://github.com/gbif/text-tree
   */
  TEXT_TREE(true, "TextTree", "Text Tree"),
  
  /**
   * COL Data Package
   * See https://github.com/CoL-Data/package-specs
   */
  COLDP(true, "ColDP", "Catalogue of Life Data Package"),
  
  /**
   * YAML based distributed archive descriptor that proxies individual remote data files into a single archive
   * and allows to semantically map columns and delimiter formats.
   * See https://github.com/Sp2000/colplus-backend/issues/518
   */
  PROXY(false, "proxy", "Proxy");


  private final String name;
  private final String title;
  private final boolean exportable;

  DataFormat(boolean exportable, String name, String title) {
    this.exportable = exportable;
    this.name = name;
    this.title = title;
  }

  public boolean isExportable() {
    return exportable;
  }

  public String getName() {
    return name;
  }

  public String getTitle() {
    return title;
  }
}
