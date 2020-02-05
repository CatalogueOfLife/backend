package life.catalogue.es.query;

import life.catalogue.es.mapping.MultiField;

public class SciNameAutoCompleteQuery extends AbstractMatchQuery {

  public SciNameAutoCompleteQuery(String field, String value) {
    super(field, value);
  }

  @Override
  protected String getField(String field) {
    return field + "." + MultiField.SCINAME_AUTO_COMPLETE.getName();
  }

}
