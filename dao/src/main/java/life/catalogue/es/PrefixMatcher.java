package life.catalogue.es;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.es.QMatcher.MAX_NGRAM_SIZE;
import static life.catalogue.es.QMatcher.MIN_NGRAM_SIZE;

interface PrefixMatcher extends MatcherMixIn {

  @Override
  default Query matchAsEpithet(String field, String term, Float boost) {
    if (term.length() > MAX_NGRAM_SIZE || term.length() < MIN_NGRAM_SIZE) {
      float b = boost != null ? boost : 0.1f * term.length();
      return Query.of(q -> q.match(m -> m.field(field + ".sac").query(term).boost(b)));
    }
    float b = boost != null ? boost : 3.5f;
    return Query.of(q -> q.match(m -> m.field(field + ".sac").query(term).operator(Operator.And).boost(b)));
  }

}
