package life.catalogue.es.suggest;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.QTranslator;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

public class QSuggestTranslater extends QTranslator {
  protected static final String FLD_SCINAME = "usage.name.scientificName.suggest";

  public QSuggestTranslater(NameUsageRequest request) {
    super(request);
  }

  @Override
  public Query buildSciNameQuery() {
    return Query.of(q -> q
      .match(m -> m
        .query(request.getQ())
        .field(FLD_SCINAME)
      )
    );
  }
}
