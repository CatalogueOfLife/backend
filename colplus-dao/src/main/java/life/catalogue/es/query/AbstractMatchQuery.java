package life.catalogue.es.query;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonValue;
import life.catalogue.es.mapping.MultiField;
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
    if (getMultiField() != null) {
      field += "." + getMultiField().getName();
    }
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

  protected abstract MultiField getMultiField();

}
