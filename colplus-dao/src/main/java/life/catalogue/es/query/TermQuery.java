package life.catalogue.es.query;

import java.util.Map;

import static java.util.Collections.singletonMap;

public class TermQuery extends ConstraintQuery<TermConstraint> {

  private final Map<String, TermConstraint> term;

  public TermQuery(String field, Object value) {
    term = singletonMap(getField(field), new TermConstraint(value));
  }

  @Override
  TermConstraint getConstraint() {
    return term.values().iterator().next();
  }

  String getField(String field) {
    return field;
  }

}
