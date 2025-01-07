package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;

import static life.catalogue.es.nu.NameUsageWrapperConverter.normalize;

/**
 * Abstract base class for fuzzy matching.
 *
 */
abstract class FuzzyMatcher extends QMatcher implements MatcherMixIn {

  FuzzyMatcher(NameUsageRequest request) {
    super(request);
  }

  @Override
  Query matchAsMonomial() {
    String[] terms = request.getSciNameSearchTerms();
    String termWN = normalize(terms[0]);
    // we used to use the strongly normalised terms to index/query species/infraspecific epithets.
    // But that caused more problems than it helped...
    String termSN = NameUsageWrapperConverter.normalize(terms[0]);
    return sciNameBaseQuery()
        .subquery(new BoolQuery() // Prefer subspecies over species and species over genera
            .should(matchAsEpithet(FLD_SUBSPECIES, termWN).withBoost(1.2))
            .should(matchAsEpithet(FLD_SPECIES, termWN).withBoost(1.1))
            .should(matchAsEpithet(FLD_GENUS, termWN).withBoost(1.0)));
  }

  @Override
  Query matchAsBinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0WN = normalize(terms[0]);
    String term0SN = NameUsageWrapperConverter.normalize(terms[0]);
    String term1WN = normalize(terms[1]);
    String term1SN = NameUsageWrapperConverter.normalize(terms[1]);
    return sciNameBaseQuery()
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .withBoost(3.0))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .withBoost(2.5))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SPECIES, term0SN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .withBoost(2.0))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SUBSPECIES, term0SN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SPECIES, term0SN))
            .must(matchAsGenericEpithet(term1WN))
            .withBoost(1.0));
  }

  @Override
  Query matchAsTrinomial() {
    String[] terms = request.getSciNameSearchTerms();
    String term0WN = normalize(terms[0]);
    String term1SN = NameUsageWrapperConverter.normalize(terms[1]);
    String term2SN = NameUsageWrapperConverter.normalize(terms[2]);
    return sciNameBaseQuery()
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term2SN))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .must(matchAsEpithet(FLD_SPECIES, term2SN))
            .withBoost(1.0));
  }

}
