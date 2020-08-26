package life.catalogue.es.nu;

import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameEdgeNgramQuery;
import life.catalogue.es.query.SciNamePrefixQuery;
import static life.catalogue.es.nu.QMatcher.MAX_NGRAM_SIZE;
import static life.catalogue.es.nu.QMatcher.MIN_NGRAM_SIZE;
import static life.catalogue.es.query.AbstractMatchQuery.Operator.AND;

interface PrefixMatcher extends MatcherMixIn {

  @Override
  default Query matchAsEpithet(String field, String term) {
    if (term.length() > MAX_NGRAM_SIZE || term.length() < MIN_NGRAM_SIZE) {
      return new SciNamePrefixQuery(field, term).withBoost(0.1 * term.length());
    }
    return new SciNameEdgeNgramQuery(field, term).withOperator(AND).withBoost(3.5);
  }

}
