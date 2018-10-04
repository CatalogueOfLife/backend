package org.col.es.mapping;

import org.col.es.annotations.Analyzer;

import static org.col.es.annotations.Analyzer.CASE_INSENSITIVE;
import static org.col.es.annotations.Analyzer.DEFAULT;
import static org.col.es.annotations.Analyzer.NGRAM0;

/**
 * A {@code MultiField} is a virtual field underneath a {@link SimpleField regular field} that
 * specifies an extra analyzer to be applied to the data stored in that field. Note that, although
 * {@code MultiField} is a subclass of {@link ESField}, calling {@link #getParent()} on a
 * {@code MultiField} will return {@code null}. In other words, you can navigate from a
 * {@link SimpleField} to its {@code MultiField} children, but not the other way round.
 */
public class MultiField extends ESField {

  /**
   * A string field analyzed using the default analyzer.
   */
  public static final MultiField DEFAULT_MULTIFIELD;
  /**
   * A string field for case-insensitive comparisons.
   */
  public static final MultiField CI_MULTIFIELD;
  /**
   * A string field for case-insensitive comparisons.
   */
  public static final MultiField NGRAM0_MULTIFIELD;

  static {
    DEFAULT_MULTIFIELD = new MultiField(DEFAULT);
    CI_MULTIFIELD = new MultiField(CASE_INSENSITIVE);
    NGRAM0_MULTIFIELD = new MultiField(NGRAM0);
  }

  private final String analyzer;

  private MultiField(Analyzer analyzer) {
    super();
    this.name = analyzer.getMultiFieldName();
    this.type = ESDataType.TEXT;
    this.analyzer = analyzer.getName();
  }

  public String getAnalyzer() {
    return analyzer;
  }

}
