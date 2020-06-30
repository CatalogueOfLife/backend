package life.catalogue.es.query;

import com.google.common.base.Preconditions;
import life.catalogue.es.ddl.Analyzer;

public class SciNameEqualsQuery extends AbstractMatchQuery {

  public SciNameEqualsQuery(String field, Object value) {
    super(field, Preconditions.checkNotNull(value, "match value must not be null").toString());
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.SCINAME_IGNORE_CASE;
  }

}
