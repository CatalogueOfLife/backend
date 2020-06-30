package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

public class SciNameMatchQuery extends AbstractMatchQuery {

  public SciNameMatchQuery(String field, String value) {
    super(field, value);
    withOperator(Operator.AND);
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.SCINAME_WHOLE_WORDS;
  }

}
