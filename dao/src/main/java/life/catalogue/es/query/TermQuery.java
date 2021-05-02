package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

import java.util.Map;

import static java.util.Collections.singletonMap;

public class TermQuery extends ConstraintQuery<TermConstraint> {

  private final Map<String, TermConstraint> term;

  public TermQuery(String field, Object value) {
    if (getAnalyzer().getMultiField() != null) {
      field += "." + getAnalyzer().getMultiField().getName();
    }
    term = singletonMap(field, new TermConstraint(value));
  }

  @Override
  protected TermConstraint getConstraint() {
    return term.values().iterator().next();
  }

  /**
   * Returns the analyzer whose "multifield" must be accessed by the term query.
   * 
   * @return The "multifield" to be accessed by the term query
   * 
   * @implNote The default term query (this one) accesses the field itself rather than any multifield underneath it. Subclasses
   *           <code>should</code> override this method, because it's pointless to have other classes doing term queries against the main
   *           field.
   */
  protected Analyzer getAnalyzer() {
    return Analyzer.KEYWORD;
  }

}
