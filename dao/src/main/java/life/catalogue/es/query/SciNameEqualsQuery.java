package life.catalogue.es.query;

import life.catalogue.es.ddl.Analyzer;

import com.google.common.base.Preconditions;

public class SciNameEqualsQuery extends AbstractMatchQuery {

  public SciNameEqualsQuery(String field, Object value) {
    super(field, Preconditions.checkNotNull(value, "match value must not be null").toString());
  }

  @Override
  protected Analyzer getAnalyzer() {
    return Analyzer.SCINAME_IGNORE_CASE;
  }

}
