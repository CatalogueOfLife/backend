package life.catalogue.es2.suggest;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es2.query.QTranslator;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

public class QSuggestTranslater extends QTranslator {

  public QSuggestTranslater(NameUsageRequest request) {
    super(request);
  }

  @Override
  public Query buildSciNameQuery() {
    return Query.of(q -> q
      .multiMatch(m -> m
        .query(request.getQ())
        .type(TextQueryType.BoolPrefix)
        .fields(FLD_SCINAME, FLD_SCINAME+"._2gram", FLD_SCINAME+"._3gram")
      )
    );
  }
}
