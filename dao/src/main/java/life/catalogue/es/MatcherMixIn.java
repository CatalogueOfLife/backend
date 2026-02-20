package life.catalogue.es;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.es.QMatcher.FLD_GENUS;
import static life.catalogue.es.QMatcher.FLD_GENUS_LETTER;

interface MatcherMixIn {

  /**
   * Creates a query matching the given term against the given epithet field.
   * If boost is null, a default boost determined by the matcher type is used.
   */
  Query matchAsEpithet(String field, String term, Float boost);

  default Query matchAsEpithet(String field, String term) {
    return matchAsEpithet(field, term, null);
  }

  default Query matchAsGenericEpithet(String term) {
    if (term.length() == 1) {
      char c = term.charAt(0);
      return Query.of(q -> q.term(t -> t.field(FLD_GENUS_LETTER).value(String.valueOf(c)).boost(0.2f)));
    } else if (term.length() == 2 && term.charAt(1) == '.') {
      char c = term.charAt(0);
      return Query.of(q -> q.term(t -> t.field(FLD_GENUS_LETTER).value(String.valueOf(c)).boost(0.4f)));
    }
    return matchAsEpithet(FLD_GENUS, term);
  }

}
