package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

import java.util.Map;

import static java.util.Collections.singletonMap;

/**
 * The startsWith or LIKE "something%" query.
 *
 */
public class PrefixQuery extends ConstraintQuery<TermConstraint> {

  private final Map<String, TermConstraint> prefix;

  public PrefixQuery(String field, Object value) {
    if (getAnalyzer().getMultiField() != null) {
      field += "." + getAnalyzer().getMultiField().getName();
    }
    prefix = singletonMap(field, new TermConstraint(value));
  }

  @Override
  TermConstraint getConstraint() {
    return prefix.values().iterator().next();
  }

  /**
   * Returns the analyzer whose "multifield" must be accessed by the term query.
   * 
   * @return The "multifield" to be accessed by the term query
   * 
   * @implNote The default term query (this one) accesses the field itself rather than any multifield underneath it. Subclasses
   *           <code>should</code> override this method, because it's pointless to have other classes doing prefix queries against the main
   *           field.
   */
  protected Analyzer getAnalyzer() {
    return Analyzer.KEYWORD;
  }

}
