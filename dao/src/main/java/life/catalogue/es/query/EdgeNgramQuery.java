package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;
import life.catalogue.es.ddl.MultiField;

public class EdgeNgramQuery extends AbstractMatchQuery {

  public EdgeNgramQuery(String field, String value) {
    super(field, value);
  }

  @Override
  protected MultiField getMultiField() {
    return Analyzer.AUTO_COMPLETE.getMultiField();
  }

}
