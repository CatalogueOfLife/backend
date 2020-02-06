package life.catalogue.es.mapping;

/**
 * Symbolic constants for the Elasticsearch analyzers defined within the CoL document store (see es-settings.json in src/main/resources).
 */
public enum Analyzer {

  /**
   * The no-op analyzer: indexes values as-is. The resulting index is accessed via the field name itself. So this analyzer is by definition
   * not associated with a multifield.
   */
  KEYWORD(null),

  /**
   * An analyzer that allows for case-insensitive searches while preserving whitespace in the search string.
   */
  IGNORE_CASE(MultiField.IGNORE_CASE),

  /**
   * An edge ngram analyzer used for auto-complete functionality. This is meant as an index-time-only analyzer. Note that in
   * es-settings.json we have defined another analyzer (autocomplete_search, which is the search-time-only counterpart of this analyzer.
   * Ordinarily, index-time and search-time analysis should be the same, except in this special case (as explained in the Elasticsearch
   * documentation). Theoretically you could index documents using the search-time analyzer, but we don't, so we don't have a constant for
   * it in this enum. See https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-edgengram-tokenizer.html
   */
  AUTO_COMPLETE(MultiField.AUTO_COMPLETE),

  /**
   * An edge ngram analyzer used for auto-complete functionality for scientific names. See {@link MultiField} for more info regarding the
   * two types of autocomplete analyzers.
   */
  SCINAME_AUTO_COMPLETE(MultiField.SCINAME_AUTO_COMPLETE);

  private final MultiField mf;

  private Analyzer(MultiField mf) {
    this.mf = mf;
  }

  public MultiField getMultiField() {
    return mf;
  }

}
