package life.catalogue.es.query;

import life.catalogue.es.ddl.MultiField;

public class SciNameCaseInsensitiveQuery extends TermQuery {

  public SciNameCaseInsensitiveQuery(String field, Object value) {
    super(field, value);
  }

  @Override
  public MultiField getMultiField() {
    return MultiField.SCINAME_IGNORE_CASE;
  }

}
