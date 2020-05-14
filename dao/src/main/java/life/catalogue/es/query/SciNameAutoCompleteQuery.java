package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.ddl.MultiField;

public class SciNameAutoCompleteQuery extends AbstractMatchQuery {

  public SciNameAutoCompleteQuery(String field, String value) {
    super(field, value);
  }

  @Override
  protected MultiField getMultiField() {
    return Analyzer.SCINAME_AUTO_COMPLETE.getMultiField();
  }

}
