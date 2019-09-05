package org.col.es.mapping;

/**
 * Symbolic constants for the Elasticsearch analyzers defined within the CoL document store (see es-settings.json in src/main/resources).
 */
public enum Analyzer {

  /**
   * The default analyzer employed by Elasticsearch, enabling full-text search. Note that, currently, none of the fields in the name usage
   * index are analyzed/indexed using this analyzer.
   */
  DEFAULT,

  /**
   * The no-op analyzer - indexes values as-is. Note that the resulting index is accessed via the field name itself, contrary to all of the
   * other indexes symbolically defined here, which are accessed via a "multi-field". See {@link MultiField}.
   */
  KEYWORD,

  /**
   * An analyzer that allows for case-insensitive searches while preserving whitespace in the search string.
   */
  IGNORE_CASE,

  /**
   * An edge ngram analyzer used for auto-complete functionality. This is meant as an index-time-only analyzer. Note that in es-settings.json
   * we have defined another analyzer (autocomplete_search, which is the search-time-only counterpart of this analyzer. Ordinarily,
   * index-time and search-time analysis should be the same, except in this special case (as explained in the Elasticsearch documentation).
   * Theoretically you could index documents using the search-time analyzer, but we don't and it's odd so we don't have a constant for it in
   * this enum. See https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-edgengram-tokenizer.html
   */
  AUTO_COMPLETE;

}
