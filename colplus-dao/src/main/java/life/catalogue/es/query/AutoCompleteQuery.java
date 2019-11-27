package life.catalogue.es.query;

import life.catalogue.es.mapping.MultiField;

public class AutoCompleteQuery extends AbstractMatchQuery {

  public AutoCompleteQuery(String field, String value) {
    super(field, value);
  }

  protected String getField(String field) {
    return field + "." + MultiField.AUTO_COMPLETE.getName();
  }

}
