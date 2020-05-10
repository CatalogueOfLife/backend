package life.catalogue.es.ddl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code MultiField} is a virtual field underneath a {@link SimpleField regular field} that specifies an extra analyzer to be applied to
 * the data stored in that field. Note that there is no multifield for no-op as-is indexing (the "keyword" analyzer). If a
 * <code>SimpleField</code> was created from a stringy Java datatype, it will by default be indexed as such. In fact if you <i>don't</i>
 * want it to be indexed as such (and you don't specify any other analyzer either), you have to explicitly decorate the Java field with
 * {@link NotIndexed}. (Alternative, you could specify an empty {@link Analyzers} array.)
 */
public class MultiField extends ESField {

  /**
   * A string field for case-insensitive comparisons.
   */
  public static final MultiField IGNORE_CASE;

  /**
   * A string field for case-insensitive "starts with" comparisons.
   */
  public static final MultiField AUTO_COMPLETE;

  /**
   * A string field for case-insensitive comparisonsfor on scientific names, filtering out some characters from the search phrase and
   * indexed value (see es-settings.json).
   */
  public static final MultiField SCINAME_IGNORE_CASE;

  /**
   * A full-text type analyzer tailored for scientific names
   */
  public static final MultiField SCINAME_WHOLE_WORDS;

  /**
   * A string field for case-insensitive "starts with" comparisons for scientific names. The main (and crucial) differences between the
   * regular and the sciname autocomplete analyzer are:
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
  public static final MultiField SCINAME_AUTO_COMPLETE;

  static {
    IGNORE_CASE = new MultiField("ic", "ignore_case");
    AUTO_COMPLETE = new MultiField("ac", "autocomplete_indextime", "autocomplete_querytime");
    SCINAME_IGNORE_CASE = new MultiField("sic", "sciname_ignore_case");
    SCINAME_WHOLE_WORDS = new MultiField("sww", "sciname_whole_words");
    SCINAME_AUTO_COMPLETE = new MultiField("sac", "sciname_autocomplete_indextime", "sciname_whole_words");
  }

  private final String analyzer;
  @JsonProperty("search_analyzer")
  private final String searchAnalyzer;

  private MultiField(String name, String analyzer) {
    this(name, analyzer, null);
  }

  /*
   * NB The name of the analyzer (and search analyzer if applicable) __must__ match the name of an analyzer defined in es-settings.json !!!
   */
  private MultiField(String name, String analyzer, String searchAnalyzer) {
    super();
    this.type = ESDataType.TEXT;
    this.name = name;
    this.analyzer = analyzer;
    this.searchAnalyzer = searchAnalyzer;
  }

  public String getAnalyzer() {
    return analyzer;
  }

  public String getSearchAnalyzer() {
    return searchAnalyzer;
  }

}
