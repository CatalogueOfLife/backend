package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.TermQuery;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeWeakly;

/**
 * Executes a query against the normalized versions of the scientific name's epithets.
 *
 */
abstract class FuzzyQMatcher extends QMatcher {

  FuzzyQMatcher(NameUsageRequest request) {
    super(request);
  }

  public Query getScientificNameQuery() {
    String[] terms = request.getSearchTerms();
    if (couldBeEpithets()) {
      if (terms.length == 1 && terms[0].length() > 2) { // Let's wait a bit before engaging this one
        return matchAsMonomial();
      } else if (terms.length == 2) {
        return matchAsBinomial();
      } else if (terms.length == 3) {
        return matchAsTrinomial();
      }
    }
    return WholeWordQMatcher.baseQuery(request);
  }

  abstract Query matchAsEpithet(String field, String term);

  private Query matchAsMonomial() {
    String[] terms = request.getSearchTerms();
    String termWN = normalizeWeakly(terms[0]);
    String termSN = normalizeStrongly(terms[0]);
    return WholeWordQMatcher.baseQuery(request)
        .subquery(new BoolQuery() // Prefer subspecies over species and species over genera
            .should(matchAsEpithet(FLD_SUBSPECIES, termSN).withBoost(1.2))
            .should(matchAsEpithet(FLD_SPECIES, termSN).withBoost(1.1))
            .should(matchAsEpithet(FLD_GENUS, termWN).withBoost(1.0)));
  }

  private Query matchAsBinomial() {
    String[] terms = request.getSearchTerms();
    String term0WN = normalizeWeakly(terms[0]);
    String term0SN = normalizeStrongly(terms[0]);
    String term1WN = normalizeWeakly(terms[1]);
    String term1SN = normalizeStrongly(terms[1]);
    return WholeWordQMatcher.baseQuery(request)
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

  private Query matchAsTrinomial() {
    String[] terms = request.getSearchTerms();
    String term0WN = normalizeWeakly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    String term2SN = normalizeStrongly(terms[2]);
    return WholeWordQMatcher.baseQuery(request)
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

  private Query matchAsGenericEpithet(String term) {
    if (term.length() == 1) {
      return new TermQuery(FLD_GENUS_LETTER, term.charAt(0)).withBoost(0.2); // Nice but not great
    } else if (term.length() == 2 && term.charAt(1) == '.') {
      return new TermQuery(FLD_GENUS_LETTER, term.charAt(0)).withBoost(0.4); // More ominous
    }
    return matchAsEpithet(FLD_GENUS, term);
  }

  private boolean couldBeEpithets() {
    String[] terms = request.getSearchTerms();
    if (terms == null) return false;
    
    if (terms.length > 3) {
      // could still all be epithets but we don't know how to deal with them any longer
      return false;
    } else if (terms.length == 1) {
      // With just one search term it's too much of a stretch to assume the user is typing the abbreviated form of a generic epithet
      return couldBeEpithet(terms[0]);
    }
    for (String term : terms) {
      if (term.length() == 1 && isEpitheticalCharacter(term.codePointAt(0))) {
        continue; // User could be typing "H sapiens", which we cater for, or even "sapiens H", which we still cater for.
      } else if (term.length() == 2 && isEpitheticalCharacter(term.codePointAt(0)) && term.charAt(1) == '.') {
        continue; // User could be typing "H. sapiens"
      } else if (!couldBeEpithet(term)) {
        return false;
      }
    }
    return true;
  }

  private static boolean couldBeEpithet(String term) {
    return term.chars().filter(i -> !isEpitheticalCharacter(i)).findFirst().isEmpty();
  }

  private static boolean isEpitheticalCharacter(int i) {
    return Character.isLetter(i);
    /*
     * In fact hyphen and apostophe are also legitimate characters in an epithet, but these are filtered out by the tokenizer, so what
     * remains is a strings of letters.
     */
  }

}
