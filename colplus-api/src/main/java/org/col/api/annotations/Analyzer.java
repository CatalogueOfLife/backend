package org.col.api.annotations;

/**
 * Symbolic constants for the Elasticsearch analyzers defined within the CoL document store.
 */
public enum Analyzer {

  /**
   * The default Elasticsearch analyzer.
   */
  DEFAULT(null, "analyzed"),

  /**
   * The no-op analyzer; indexes values as-is.
   */
  KEYWORD("keyword", null),
  /**
   * An analyzer that allows for case-insensitive searches while preserving whitespace in the search
   * string.
   */
  CASE_INSENSITIVE("case_insensitive_analyzer", "ci"),

  /**
   * An ngram analyzer that breaks values up into ngrams of size 3 to 15.
   */
  NGRAM0("ngram0_analyzer", "ngram0");

  private final String name;
  private final String multiFieldName;

  private Analyzer(String name, String multiFieldName) {
    this.name = name;
    this.multiFieldName = multiFieldName;
  }

  /**
   * The name of the analyzer.
   */
  /*
   * This name must correspond to an analyzer defined in es-settings.json !
   */
  public String getName() {
    return name;
  }

  /**
   * The name of the multifield through which to invoke the analyzer.
   */
  public String getMultiFieldName() {
    return multiFieldName;
  }

  @Override
  public String toString() {
    return name;
  }

}
