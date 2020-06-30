package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class CaseInsensitivePrefixQuery extends PrefixQuery {

  public CaseInsensitivePrefixQuery(String field, Object value) {
    super(field, value.toString().toLowerCase());
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.IGNORE_CASE;
  }

}
