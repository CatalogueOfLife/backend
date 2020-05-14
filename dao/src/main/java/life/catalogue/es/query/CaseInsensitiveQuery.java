package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.ddl.MultiField;

public class CaseInsensitiveQuery extends TermQuery {

  public CaseInsensitiveQuery(String field, Object value) {
    super(field, value);
  }

  @Override
  protected MultiField getMultiField() {
    return Analyzer.IGNORE_CASE.getMultiField();
  }

}
