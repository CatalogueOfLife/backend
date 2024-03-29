package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class EdgeNgramQuery extends AbstractMatchQuery {

  public EdgeNgramQuery(String field, String value) {
    super(field, value);
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.AUTO_COMPLETE;
  }

}
