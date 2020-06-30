package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeWeakly;

/**
 * Abstract base class for non-fuzzy matching.
 */
abstract class SimpleMatcher extends QMatcher implements MatcherMixIn {

  SimpleMatcher(NameUsageRequest request) {
    super(request);
  }

  @Override
  Query matchAsMonomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0 = normalizeWeakly(terms[0]);
    return sciNameBaseQuery()
        .subquery(new BoolQuery() // Prefer subspecies over species and species over genera
            .should(matchAsEpithet(FLD_SUBSPECIES, term0).withBoost(1.2))
            .should(matchAsEpithet(FLD_SPECIES, term0).withBoost(1.1))
            .should(matchAsEpithet(FLD_GENUS, term0).withBoost(1.0)));
  }

  @Override
  Query matchAsBinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0 = normalizeWeakly(terms[0]);
    String term1 = normalizeWeakly(terms[1]);
    return sciNameBaseQuery()
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1))
            .withBoost(3.0))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0))
            .must(matchAsEpithet(FLD_SPECIES, term1))
            .withBoost(2.5))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SPECIES, term0))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1))
            .withBoost(2.0))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SUBSPECIES, term0))
            .must(matchAsEpithet(FLD_SPECIES, term1))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SPECIES, term0))
            .must(matchAsGenericEpithet(term1))
            .withBoost(1.0));
  }

  @Override
  Query matchAsTrinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0 = terms[0];
    String term1 = terms[1];
    String term2 = terms[2];
    return sciNameBaseQuery()
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0))
            .must(matchAsEpithet(FLD_SPECIES, term1))
            .must(matchAsEpithet(FLD_SUBSPECIES, term2))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1))
            .must(matchAsEpithet(FLD_SPECIES, term2))
            .withBoost(1.0));
  }

}
