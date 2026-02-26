package life.catalogue.es.query;

import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;

import co.elastic.clients.util.ObjectBuilder;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.api.search.NameUsageRequest.SearchContent.*;

public class QTranslator {
  protected static final String FLD_SCINAME = "usage.name.scientificName";
  static final String FLD_AUTHORSHIP = "usage.name.authorship";
  static final String FLD_VERNACULAR = "vernacularNames.name";
  /**
   * The extra boost to give to exact scientific name or authorship matches.
   */
  public static final float EXTRA_BOOST = 10.0f;

  protected final NameUsageRequest request;

  public QTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public Query translate() {
    List<Query> queries = new ArrayList<>(request.getContent().size());
    if (request.getContent().contains(SCIENTIFIC_NAME)) {
      // always make sure the exact match is first
      if (request.getSearchType() != NameUsageRequest.SearchType.EXACT) {
        queries.add(withBoost(buildSciNameExactQuery(), 2*EXTRA_BOOST));
      }
      queries.add(buildSciNameQuery());
    }
    if (request.getContent().contains(AUTHORSHIP)) {
      // always make sure the exact match is first
      if (request.getSearchType() != NameUsageRequest.SearchType.EXACT) {
        queries.add(withBoost(buildAuthorshipExactQuery(), 2.0f));
      }
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
    return Query.of(q -> q
      .bool(b -> b
        .must(query)
        .boost(boost)
      )
    );
  }

  /**
   * Wraps a query with a function score query that boosts accepted names.
   * @param query the main query to wrap
   */
  private Query boostAcceptedQuery(Function<Query.Builder, ObjectBuilder<Query>> query) {
    return Query.of(q -> q
      .functionScore(fs -> fs
        .query(query)
        .functions(f -> f
          .filter(fq -> fq
            .term(t -> t
              .field("usage.status")
              .value("ACCEPTED")
            )
          )
          .weight(1.2)
        )
        .scoreMode(FunctionScoreMode.Multiply)
        .boostMode(FunctionBoostMode.Multiply)
      )
    );
  }

  public Query buildSciNameExactQuery() {
    return boostAcceptedQuery(q -> q
      .term(t -> t
        .field(FLD_SCINAME)
        .value(request.getQ())
      )
    );
  }

  public Query buildSciNameQuery() {
    // default to WHOLE_WORDS
    Query query = switch (ObjectUtils.coalesce(request.getSearchType(), NameUsageRequest.SearchType.WHOLE_WORDS)) {
      case EXACT
        -> buildSciNameExactQuery();
      case PREFIX
        -> boostAcceptedQuery(q -> q
          .prefix(p -> p
            .field(FLD_SCINAME)
            .value(request.getQ())
          )
        );
      case WHOLE_WORDS
        -> boostAcceptedQuery(q -> q
          .match(t -> t.field("usage.name.scientificName.word")
            .query(request.getQ())
          )
        );
      case FUZZY
        -> boostAcceptedQuery(q -> q
          .fuzzy(f -> f
            .field(FLD_SCINAME+"Normalized")
            .value(request.getQ())
            .fuzziness("AUTO")
            .prefixLength(2)
          )
        );
    };
    return withBoost(query, EXTRA_BOOST);
  }

  public Query buildAuthorshipExactQuery() {
    return boostAcceptedQuery(q -> q
      .term(t -> t
        .field(FLD_AUTHORSHIP)
        .value(request.getQ())
      )
    );
  }

  public Query buildAuthorshipQuery() {
    return switch (ObjectUtils.coalesce(request.getSearchType(), NameUsageRequest.SearchType.WHOLE_WORDS)) {
      case EXACT -> buildAuthorshipExactQuery();
      case PREFIX -> boostAcceptedQuery(q -> q
        .prefix(p -> p
          .field(FLD_AUTHORSHIP)
          .value(request.getQ())
        )
      );
      case WHOLE_WORDS, FUZZY -> boostAcceptedQuery(q -> q
        .match(t -> t.field(FLD_AUTHORSHIP + ".word")
          .query(request.getQ())
        )
      );
    };
  }

  public Query buildVernacularQuery() {
    return boostAcceptedQuery(q -> q
      .match(m -> m.field(FLD_VERNACULAR)
        .query(request.getQ())
      )
    );
  }
}
