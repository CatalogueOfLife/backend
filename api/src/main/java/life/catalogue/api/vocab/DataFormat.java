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
  PROXY(false, "proxy", "Proxy"),

  /**
   * The Newick tree format is a way of representing graph-theoretical trees with edge lengths using parentheses and commas.
   * It is often used with phylogenetic data.
   * https://en.wikipedia.org/wiki/Newick_format
   */
  NEWICK(true, "Newick", "Newick Tree Format"),

  /**
   * The Graphviz DOT format for representing graphs as nodes and edges.
   * http://www.graphviz.org/doc/info/lang.html
   */
  DOT(true, "DOT", "Graphviz DOT");

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

  public String getSuffix() {
    return name().toLowerCase().replaceAll("_", "");
  }
}
