package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class SciNamePrefixQuery extends PrefixQuery {

  public SciNamePrefixQuery(String field, Object value) {
    super(field, value);
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.SCINAME_IGNORE_CASE;
  }

}
