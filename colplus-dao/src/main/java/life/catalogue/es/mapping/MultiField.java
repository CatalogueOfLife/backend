package life.catalogue.es.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.es.model.NameStrings;

/**
 * A {@code MultiField} is a virtual field underneath a {@link SimpleField regular field} that specifies an extra analyzer to be applied to
 * the data stored in that field. Note that, although {@code MultiField} is a subclass of {@link ESField}, calling {@link #getParent()} on a
 * {@code MultiField} will return {@code null}. In other words, you can navigate from a {@link SimpleField} to its {@code MultiField}
 * children, but not the other way round.
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
   * A string field for case-insensitive "starts with" comparisons for scientific names. The main (and crucial) differences between the
   * regular and the sciname autocomplete analyzer are:
   * <ol>
   * <li>The sciname analyzer removes parentheses <i>before</i> tokenization, so that "H(eterodon)" becomes "Heterodon". The default
   * autocomplete analyzer would simply tokenize on the '(' character, so that "H(eterodon)" would be split into "H" and "eterodon", which
   * would match the first letter of the intended genus (too short given the minimum ngram length), but not the entire generic epithet.
   * <li>The sciname autocomplete analyzer tokenizes only on sure bets: whitespace, comma and '×'. The default autocomplete analyzer
   * tokenizes on anything that's not a letter or digit. The former behaviour is especially targeted at searching for unparsed name like
   * "MV-L51 ICTV" and "SH215351.07FU".
   * </ol>
   * Note that the individual epithet of the scientific name (in the {@link NameStrings} class are analyzed using the default autocomplete
   * analyzer!! Mostly because it doesn't really matter that much in case of single-word, letter-only entries.
   */
  public static final MultiField SCINAME_AUTO_COMPLETE;

  static {
    IGNORE_CASE = new MultiField("ic", "ignore_case");
    AUTO_COMPLETE = new MultiField("dac", "default_autocomplete_indextime", "default_autocomplete_querytime");
    SCINAME_AUTO_COMPLETE = new MultiField("sac", "sciname_autocomplete_indextime", "sciname_autocomplete_querytime");
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
