package org.col.importer.neo.printer;

/**
 *
 */
public enum GraphFormat {
  /**
   * Taxonomic tree as nested text rows
   */
  TEXT("txt"),
  
  /**
   * Graph modelling language
   * See http://www.fim.uni-passau.de/fileadmin/files/lehrstuhl/brandenburg/projekte/gml/gml-technical-report.pdf
   */
  GML("gml"),
  
  /**
   * http://www.graphviz.org/doc/info/lang.html
   */
  DOT("dot"),
  
  /**
   * Tab delimited format used for nub sources in integration tests
   */
  TAB("tab"),
  
  /**
   * Denormalised queue of genus or infrageneric names. Not tree format really.
   */
  LIST("txt");
  
  public final String suffix;
  
  GraphFormat(String suffix) {
    this.suffix = suffix;
  }
}
