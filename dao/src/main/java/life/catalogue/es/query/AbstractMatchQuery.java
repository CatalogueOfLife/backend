package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

import static java.util.Collections.singletonMap;

/**
 * Base class for queries against analyzed fields (as opposed to term queries).
 *
 */
public abstract class AbstractMatchQuery extends ConstraintQuery<MatchConstraint> {

  /**
   * Determines how to join the subqueries for the terms in the search phrase.
   *
   */
  public static enum Operator {
    AND, OR;

    @JsonValue
    public String toString() {
      return name();
    }
  }

  private final Map<String, MatchConstraint> match;

  public AbstractMatchQuery(String field, String value) {
    Preconditions.checkNotNull(getAnalyzer(), "Analyzer must be specified for match queries");
    field += "." + getAnalyzer().getMultiField().getName();
    match = singletonMap(field, new MatchConstraint(value));
  }

  public AbstractMatchQuery withOperator(AbstractMatchQuery.Operator operator) {
    getConstraint().operator(operator);
    return this;
  }

  @Override
  protected MatchConstraint getConstraint() {
    return match.values().iterator().next();
  }

  /**
   * Returns the {@link Analyzer} whose "multifield"specifies the index to be accessed (e.g. the full-text index, the edge ngram index,
   * etc.).
   * 
   * @return
   */
  protected abstract Analyzer getAnalyzer();

}
