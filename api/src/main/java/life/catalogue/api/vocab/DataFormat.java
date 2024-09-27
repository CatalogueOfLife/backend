package life.catalogue.api.vocab;

/**
 * Various external data formats supported by the application
 */
public enum DataFormat {
  
  /**
   * Darwin Core Archive
   */
  DWCA("DwCA", "dwca", "Darwin Core Archive"),
  
  /**
   * COL Data Submission Format ("Annual Checklist Exchange Format")
   * https://www.checklistbank.org/about/formats#annual-checklist-exchange-format-acef
   *
   * https://www.catalogueoflife.org/images/acef/2014_CoL_Standard_Dataset_v7_23Sep2014.pdf
   * List_of_tables_and_fields_2014.pdf
   * ERD_DataSubmissionFormat_29Sep2014.pdf
   */
  ACEF("ACEF", "acef", "Annual Checklist Exchange Format"),
  
  /**
   * Indented plain text trees for very simple but readable classifications without structured names,
   * but supporting synonyms and basionyms.
   * https://github.com/gbif/text-tree
   * TXTREE
   */
  TEXT_TREE("TextTree", "txtree", "TextTree"),
  
  /**
   * COL Data Package
   * See https://github.com/CatalogueOfLife/coldp
   */
  COLDP("ColDP", "coldp", "Catalogue of Life Data Package"),
  
  /**
   * YAML based distributed archive descriptor that proxies individual remote data files into a single archive
   * and allows to semantically map columns and delimiter formats.
   * See https://github.com/Sp2000/colplus-backend/issues/518
   */
  PROXY("proxy", "proxy","Proxy"),

  /**
   * The Newick format is a way of representing graph-theoretical trees with edge lengths using parentheses and commas.
   * It is often used with phylogenetic data.
   * The New Hampshire eXtended format (which we implement) uses Newick comments to encode additional key value pairs, e.g. the id, scientificName ond rank.
   * https://en.wikipedia.org/wiki/Newick_format
   * http://www.phylosoft.org/NHX/
   */
  NEWICK("Newick","newick", "New Hampshire X"),

  /**
   * The Graphviz DOT format for representing graphs as nodes and edges.
   * http://www.graphviz.org/doc/info/lang.html
   */
  DOT("DOT", "dot", "Graphviz DOT");

  private final String name;
  private final String title;
  private final String filename;

  DataFormat(String name, String filename, String title) {
    this.name = name;
    this.title = title;
    this.filename = filename;
  }

  public String getName() {
    return name;
  }

  public String getTitle() {
    return title;
  }

  public String getFilename() {
    return filename;
  }

  /**
   * Advertises if the format supports a download with the extended option turned on.
   */
  public boolean hasExtendedContent() {
    return this == DWCA || this == COLDP || this == TEXT_TREE || this == NEWICK;
  }
}
