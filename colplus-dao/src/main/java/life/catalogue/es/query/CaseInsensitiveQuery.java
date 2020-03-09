package life.catalogue.es.query;

import life.catalogue.es.mapping.MultiField;

public class CaseInsensitiveQuery extends TermQuery {

  public CaseInsensitiveQuery(String field, Object value) {
    super(field, value);
  }

  @Override
  protected MultiField getMultiField() {
    return MultiField.IGNORE_CASE;
  }

}
