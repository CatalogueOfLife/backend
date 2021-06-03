package life.catalogue.es.ddl;

import java.util.Set;

import com.google.common.base.Preconditions;

/**
 * Symbolic constants for the Elasticsearch analyzers defined within the CoL document store (see es-settings.json in src/main/resources).
 */
public enum Analyzer {
  /**
   * The no-op analyzer: indexes values as-is. The resulting index is accessed via the field name itself. So this analyzer is by definition
   * not associated with a multifield.
   */
  KEYWORD,

  /**
   * An analyzer that allows for case-insensitive searches while preserving whitespace in the search string.
   */
  IGNORE_CASE("ic", "ignore_case"),

  /**
   * A standard analyzer that additionally folds strings to ASCII.
   */
  STANDARD_ASCII("std", "standard_ascii"),

  /**
   * An edge ngram analyzer used for auto-complete functionality. This is meant as an index-time-only analyzer. Note that in
   * es-settings.json we have defined another analyzer (autocomplete_search, which is the search-time-only counterpart of this analyzer.
   * Ordinarily, index-time and search-time analysis should be the same, except in this special case (as explained in the Elasticsearch
   * documentation). Theoretically you could index documents using the search-time analyzer, but we don't, so we don't have a constant for
   * it in this enum. See https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-edgengram-tokenizer.html
   */
  AUTO_COMPLETE("ac", "autocomplete_indextime", "autocomplete_querytime"),

  /**
   * An analyzer that allows for case-insensitive searches while preserving whitespace in the search string.
   */
  SCINAME_IGNORE_CASE("sic", "sciname_ignore_case"),
  
  /**
   * A full-text analyzer for scientific names.
   */
  SCINAME_WHOLE_WORDS("sww", "sciname_whole_words"),

  /**
   * An edge ngram analyzer used for auto-complete functionality for scientific names.
   * The main (and crucial) differences between the regular and the sciname autocomplete analyzer are:
   * <ol>
   * <li>The sciname analyzer removes parentheses <i>before</i> tokenization, so that "H(eterodon)" becomes "Heterodon". The default
   * autocomplete analyzer would simply tokenize on the '(' character, so that "H(eterodon)" would be split into "H" and "eterodon", which
   * would match the first letter of the intended genus (too short given the minimum ngram length), but not the entire generic epithet.
   * <li>The default autocomplete analyzer currently conforms to standard edge-ngram analysis (not particularly targeted at vernacular names
   * or authors). The sciname autocomplete analyzer tokenizes only on whitespace, while filtering out some characters (see
   * es-settings.json). This behaviour is especially targeted at searching for unparsed name like "MV-L51 ICTV" and "SH215351.07FU".
   * </ol>
   * <p>
   * Note that the <i>query-time</i> analyzer for this multifield is the same as "whole words analyzer" for scientific names!! No oversight.
   * Originally we had a separate query-time analyzer for this field, but as whole-word analysis became a requirement, the two turned out to
   * be the same so we ditched the original query-time analyzer.
   */
  SCINAME_AUTO_COMPLETE("sac", "sciname_autocomplete_indextime", "sciname_whole_words");

  private final MultiField mf;

  Analyzer() {
    this.mf = null;
  }
  
  Analyzer(String name, String analyzer) {
    this(name, analyzer, null);
  }
  
  Analyzer(String name, String analyzer, String searchAnalyzer) {
    this.mf = new MultiField(name, analyzer, searchAnalyzer);
  }

  public MultiField getMultiField() {
    return mf;
  }

  // validateAvailableAnalyzers
  static {
    Set<String> definedAnalyzers = IndexDefinition.loadDefaults()
        .getSettings()
        .getAnalysis()
        .getAnalyzer()
        .keySet();

    for (Analyzer a : Analyzer.values()) {
      MultiField mf = a.getMultiField();
      if (mf != null) {
        Preconditions.checkArgument(definedAnalyzers.contains(mf.getAnalyzer()), "Analyzer %s not defined in es-settings.json", mf.getAnalyzer());
        Preconditions.checkArgument(mf.getSearchAnalyzer() == null || definedAnalyzers.contains(mf.getSearchAnalyzer()),
          "Search analyzer %s not defined in es-settings.json", mf.getSearchAnalyzer());
      }
    }
  }
}
