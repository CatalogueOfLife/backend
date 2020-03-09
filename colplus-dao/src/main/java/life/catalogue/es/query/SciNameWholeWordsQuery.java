package life.catalogue.es.query;

import life.catalogue.es.mapping.MultiField;

public class SciNameWholeWordsQuery extends AbstractMatchQuery {

  public SciNameWholeWordsQuery(String field, String value) {
    super(field, value);
    withOperator(Operator.AND);
  }

  @Override
  protected MultiField getMultiField() {
    return MultiField.SCINAME_WHOLE_WORDS;
  }

}
