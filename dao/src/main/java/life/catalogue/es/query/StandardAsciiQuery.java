package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class StandardAsciiQuery extends AbstractMatchQuery {

  public StandardAsciiQuery(String field, Object value) {
    super(field, value.toString());
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.STANDARD_ASCII;
  }

}
