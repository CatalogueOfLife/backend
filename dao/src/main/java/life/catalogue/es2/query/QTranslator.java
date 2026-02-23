package life.catalogue.es2.query;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchRequest;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import life.catalogue.api.util.ObjectUtils;

import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.*;

public class QTranslator {
  static final String FLD_SCINAME = "usage.name.scientificName";
  static final String FLD_AUTHORSHIP = "usage.name.authorship";
  static final String FLD_VERNACULAR = "vernacularNames.name";

  /**
   * The extra boost to give to scientific name matches (versus authorship matches), reflecting the fact that, all being
   * equal, we'd like to see them higher up in the list of matches.
   */
  public static final float SCINAME_EXTRA_BOOST = 10.0f;

  private final NameUsageSearchRequest request;

  public QTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  public Query translate() {
    List<Query> queries = new ArrayList<>(request.getContent().size());
    if (request.getContent().contains(SCIENTIFIC_NAME)) {
      queries.add(withBoost(buildSciNameQuery(), SCINAME_EXTRA_BOOST));
    }
    if (request.getContent().contains(AUTHORSHIP)) {
      queries.add(buildAuthorshipQuery());
    }
    if (request.getContent().contains(VERNACULAR_NAME)) {
      queries.add(buildVernacularQuery());
    }
    if (queries.size() == 1) {
      return queries.get(0);
    }
    return Query.of(q -> q.bool(b -> {
      queries.forEach(b::should);
      return b;
    }));
  }

  /**
   * Wraps a query with an explicit boost value.
   */
  public Query withBoost(Query query, float boost) {
    return Query.of(q -> q.bool(b -> b.must(query).boost(boost)));
  }

  public Query buildSciNameSuggestQuery() {
    return Query.of(q -> q
      .multiMatch(m -> m
        .query(request.getQ())
        .type(TextQueryType.BoolPrefix)
        .fields(FLD_SCINAME, FLD_SCINAME+"._2gram", FLD_SCINAME+"._3gram")
      )
    );
  }

  public Query buildSciNameQuery() {
    // default to WHOLE_WORDS
    switch (ObjectUtils.coalesce(request.getSearchType(), NameUsageRequest.SearchType.WHOLE_WORDS)) {
      case EXACT:
        return Query.of(q -> q.term(t -> t.field(FLD_SCINAME).value(request.getQ())));
      case PREFIX:
        return Query.of(q -> q
          .prefix(p -> p
            .field(FLD_SCINAME)
            .value(request.getQ())
          )
        );
      case WHOLE_WORDS:
        return Query.of(q -> q.term(t -> t.field("usage.name.scientificName.word").value(request.getQ())));
      case FUZZY:
        return Query.of(q -> q
          .fuzzy(f -> f
            .field(FLD_SCINAME+"Normalized")
            .value(request.getQ())
            .fuzziness("AUTO")
            .prefixLength(2)
          )
        );
      default:
        throw new AssertionError();
    }
  }

  public Query buildAuthorshipQuery() {
    return Query.of(q -> q.term(t -> t.field(FLD_AUTHORSHIP).value(request.getQ())));
  }

  public Query buildVernacularQuery() {
    return Query.of(q -> q.term(t -> t.field(FLD_VERNACULAR).value(request.getQ())));
  }
}
