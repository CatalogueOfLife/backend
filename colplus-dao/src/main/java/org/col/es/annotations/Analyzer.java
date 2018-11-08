package org.col.es.annotations;

/**
 * Symbolic constants for the Elasticsearch analyzers defined within the CoL document store.
 */
public enum Analyzer {
  
  /**
   * The default (full-text) analyzer. Not specifying any analyzer means the field is indexed using
   * this analyzer, hence the analyzer's name is left empty (null).
   */
  DEFAULT,
  
  /**
   * The no-op analyzer; indexes values as-is. Since this index is access through the field name
   * itself (not through a multi-field underneath it), the multi-field name is left empty (null).
   */
  KEYWORD,
  /**
   * An analyzer that allows for case-insensitive searches while preserving whitespace in the search
   * string.
   */
  IGNORE_CASE,
  
  /**
   * An analyzer typically used for auto-complete functionality. This is meant as an index-time only
   * analyzer. Note that in es-settings.json we have defined another analyzer (autocomplete_search,
   * which is the search-time only counterpart of this analyzer. Ordinarily, index-time and
   * search-time analysis should be the same, exception in this special case. You could index a
   * field using this analyzer, but there's currently no use case for it (hence there's no constant
   * for it in this enum. See
   * https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-edgengram-tokenizer.html
   */
  AUTO_COMPLETE;
  
}
