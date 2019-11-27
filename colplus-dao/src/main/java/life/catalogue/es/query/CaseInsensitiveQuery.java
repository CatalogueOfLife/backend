package life.catalogue.es.query;

import life.catalogue.es.mapping.MultiField;

public class CaseInsensitiveQuery extends TermQuery {

  public CaseInsensitiveQuery(String field, Object value) {
    super(field, value);
  }

  protected String getField(String field) {
    return field + "." + MultiField.IGNORE_CASE.getName();
  }

}
