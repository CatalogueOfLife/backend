package life.catalogue.es.query;

import life.catalogue.es.mapping.MultiField;

public class AutoCompleteQuery extends AbstractMatchQuery {

  public AutoCompleteQuery(String field, String value) {
    super(field, value);
  }

  @Override
  protected MultiField getMultiField() {
    return MultiField.AUTO_COMPLETE;
  }

}
