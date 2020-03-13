package life.catalogue.es.query;

import java.util.Map;
import life.catalogue.es.ddl.MultiField;
import static java.util.Collections.singletonMap;

public class TermQuery extends ConstraintQuery<TermConstraint> {

  private final Map<String, TermConstraint> term;

  public TermQuery(String field, Object value) {
    if (getMultiField() != null) {
      field += "." + getMultiField().getName();
    }
    term = singletonMap(field, new TermConstraint(value));
  }

  @Override
  protected TermConstraint getConstraint() {
    return term.values().iterator().next();
  }

  /**
   * Returns the "multifield" to be accessed by the term query.
   * 
   * @return The "multifield" to be accessed by the term query
   * 
   * @implNote The default term query (this one) accesses the field itself rather than any multifield underneath it and therefore returns
   *           <code>null</code>. Subclasses <code>should</code> override this method even though an implementation is provided here,
   *           because it's pointless to have multiple classes doing term queries against the main field or against the same multifield.
   */
  protected MultiField getMultiField() {
    return null;
  }

}
