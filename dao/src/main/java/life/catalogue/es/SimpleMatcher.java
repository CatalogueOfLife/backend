package life.catalogue.es;

import life.catalogue.api.search.NameUsageRequest;

import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.es.NameUsageWrapperConverter.normalizeWeakly;

/**
 * Abstract base class for non-fuzzy matching. Search terms are not normalized, so they can only hit the non-normalized versions of the
 * epithets in the {@link NameStrings} object inside a nameusage document. Note though that they still go through the Elasticsearch analysis
 * phase, even for a relative simple "equals ignore case" comparison. This is relevant because this comparison is in fact not so trivial: a
 * lot of characters are filtered out before the comparison takes place. See schema.json.
 */
abstract class SimpleMatcher extends QMatcher implements MatcherMixIn {

  SimpleMatcher(NameUsageRequest request) {
    super(request);
  }

  @Override
  Query matchAsMonomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0 = normalizeWeakly(terms[0]);
    List<Query> queries = sciNameBaseQueries();
    queries.add(Query.of(q -> q.bool(b -> b // Prefer genus over species over subspecies
        .should(matchAsEpithet(FLD_SUBSPECIES, term0, 1.0f))
        .should(matchAsEpithet(FLD_SPECIES, term0, 1.1f))
        .should(matchAsEpithet(FLD_INFRAGENERIC, term0, 1.2f))
        .should(matchAsEpithet(FLD_GENUS, term0, 1.3f))
    )));
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

  @Override
  Query matchAsBinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0 = normalizeWeakly(terms[0]);
    String term1 = normalizeWeakly(terms[1]);
    List<Query> queries = sciNameBaseQueries();
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0))
        .must(matchAsEpithet(FLD_SPECIES, term1))
        .boost(3.0f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0))
        .must(matchAsEpithet(FLD_SUBSPECIES, term1))
        .boost(2.5f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0))
        .must(matchAsEpithet(FLD_INFRAGENERIC, term1))
        .boost(2.3f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsEpithet(FLD_SPECIES, term0))
        .must(matchAsEpithet(FLD_SUBSPECIES, term1))
        .boost(2.0f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsEpithet(FLD_SUBSPECIES, term0))
        .must(matchAsEpithet(FLD_SPECIES, term1))
        .boost(1.5f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsEpithet(FLD_SPECIES, term0))
        .must(matchAsGenericEpithet(term1))
        .boost(1.0f))));
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

  @Override
  Query matchAsTrinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0 = terms[0];
    String term1 = terms[1];
    String term2 = terms[2];
    List<Query> queries = sciNameBaseQueries();
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0))
        .must(matchAsEpithet(FLD_SPECIES, term1))
        .must(matchAsEpithet(FLD_SUBSPECIES, term2))
        .boost(2.0f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0))
        .must(matchAsEpithet(FLD_SUBSPECIES, term1))
        .must(matchAsEpithet(FLD_SPECIES, term2))
        .boost(1.0f))));
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

}
