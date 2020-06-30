package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class SciNameEdgeNgramQuery extends AbstractMatchQuery {

  public SciNameEdgeNgramQuery(String field, String value) {
    super(field, value);
  }

  @Override
  protected Analyzer getAnalyzer() {
    return  Analyzer.SCINAME_AUTO_COMPLETE;
  }

}
