package life.catalogue.es.query;

import java.util.Map;

import static java.util.Collections.singletonMap;

/**
 * The startsWith or LIKE "something%" query.
 *
 */
public class PrefixQuery extends ConstraintQuery<TermConstraint> {

  private final Map<String, TermConstraint> prefix;

  public PrefixQuery(String field, Object value) {
    prefix = singletonMap(getField(field), new TermConstraint(value));
  }

  @Override
  TermConstraint getConstraint() {
    return prefix.values().iterator().next();
  }

  /*
   * A prefix query targets the field itself, not some multified underneath it.
   */
  String getField(String field) {
    return field;
  }

}
