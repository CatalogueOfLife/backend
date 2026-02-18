package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;

import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeWeakly;

/**
 * Abstract base class for fuzzy matching.
 */
abstract class FuzzyMatcher extends QMatcher implements MatcherMixIn {

  FuzzyMatcher(NameUsageRequest request) {
    super(request);
  }

  @Override
  Query matchAsMonomial() {
    String[] terms = request.getSciNameSearchTerms();
    String termWN = normalizeWeakly(terms[0]);
    List<Query> queries = sciNameBaseQueries();
    queries.add(Query.of(q -> q.bool(b -> b // Prefer subspecies over species and species over genera
        .should(matchAsEpithet(FLD_SUBSPECIES, termWN, 1.3f))
        .should(matchAsEpithet(FLD_SPECIES, termWN, 1.2f))
        .should(matchAsEpithet(FLD_INFRAGENERIC, termWN, 1.1f))
        .should(matchAsEpithet(FLD_GENUS, termWN, 1.0f))
    )));
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

  @Override
  Query matchAsBinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0WN = normalizeWeakly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    List<Query> queries = sciNameBaseQueries();
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0WN))
        .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
        .boost(3.0f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0WN))
        .must(matchAsEpithet(FLD_SPECIES, term1SN))
        .boost(2.5f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0WN))
        .must(matchAsEpithet(FLD_INFRAGENERIC, term1SN))
        .boost(2.3f))));
    String term0SN = normalizeStrongly(terms[0]);
    String term1SN2 = normalizeStrongly(terms[1]);
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsEpithet(FLD_SPECIES, term0SN))
        .must(matchAsEpithet(FLD_SUBSPECIES, term1SN2))
        .boost(2.0f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsEpithet(FLD_SUBSPECIES, term0SN))
        .must(matchAsEpithet(FLD_SPECIES, term1SN2))
        .boost(1.5f))));
    String term1WN = normalizeWeakly(terms[1]);
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsEpithet(FLD_SPECIES, term0SN))
        .must(matchAsGenericEpithet(term1WN))
        .boost(1.0f))));
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

  @Override
  Query matchAsTrinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0WN = normalizeWeakly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    String term2SN = normalizeStrongly(terms[2]);
    List<Query> queries = sciNameBaseQueries();
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0WN))
        .must(matchAsEpithet(FLD_SPECIES, term1SN))
        .must(matchAsEpithet(FLD_SUBSPECIES, term2SN))
        .boost(1.5f))));
    queries.add(Query.of(q -> q.bool(b -> b
        .must(matchAsGenericEpithet(term0WN))
        .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
        .must(matchAsEpithet(FLD_SPECIES, term2SN))
        .boost(1.0f))));
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

}
