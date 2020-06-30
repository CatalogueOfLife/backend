package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class CaseInsensitiveQuery extends AbstractMatchQuery {

  public CaseInsensitiveQuery(String field, Object value) {
    super(field, value.toString());
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.IGNORE_CASE;
  }

}
