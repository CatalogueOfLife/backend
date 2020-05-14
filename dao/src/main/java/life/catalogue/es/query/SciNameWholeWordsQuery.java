package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.ddl.MultiField;

public class SciNameWholeWordsQuery extends AbstractMatchQuery {

  public SciNameWholeWordsQuery(String field, String value) {
    super(field, value);
    withOperator(Operator.AND);
  }

  @Override
  protected MultiField getMultiField() {
    return Analyzer.SCINAME_WHOLE_WORDS.getMultiField();
  }

}
