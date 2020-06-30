package life.catalogue.es.nu;

import life.catalogue.es.query.Query;
import life.catalogue.es.query.TermQuery;
import static life.catalogue.es.nu.QMatcher.FLD_GENUS;
import static life.catalogue.es.nu.QMatcher.FLD_GENUS_LETTER;

interface MatcherMixIn {

  Query matchAsEpithet(String field, String term);

  default Query matchAsGenericEpithet(String term) {
    if (term.length() == 1) {
      return new TermQuery(FLD_GENUS_LETTER, term.charAt(0)).withBoost(0.2); // Nice but not great
    } else if (term.length() == 2 && term.charAt(1) == '.') {
      return new TermQuery(FLD_GENUS_LETTER, term.charAt(0)).withBoost(0.4); // More ominous
    }
    return matchAsEpithet(FLD_GENUS, term);
  }

}
