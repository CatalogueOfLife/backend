package life.catalogue.es.nu;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * Mix-in interface for whole-word matching
 */
interface WholeWordMatcher extends MatcherMixIn {

  @Override
  default Query matchAsEpithet(String field, String term, Float boost) {
    float b = boost != null ? boost : 5.0f;
    return Query.of(q -> q.match(m -> m.field(field + ".sic").query(term).boost(b)));
  }

}
