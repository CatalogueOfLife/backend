package life.catalogue.es.name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.mapping.MultiField;
import life.catalogue.es.query.AutoCompleteQuery;
import life.catalogue.es.query.CaseInsensitivePrefixQuery;
import life.catalogue.es.query.DisMaxQuery;
import life.catalogue.es.query.Query;
import static life.catalogue.es.query.AbstractMatchQuery.Operator.AND;

/**
 * Generates the queries for the suggest service and the search service. See also {@link MultiField} for consideration about how & why.
 */
public abstract class QMatcher {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(QMatcher.class);

  static final int MAX_NGRAM_SIZE = 10; // see es-settings.json

  static final String FLD_SCINAME = "scientificName";
  static final String FLD_GENUS = "nameStrings.genusOrMonomialWN";
  static final String FLD_GENUS_LETTER = "nameStrings.genusLetter";
  static final String FLD_SPECIES = "nameStrings.specificEpithetSN";
  static final String FLD_SUBSPECIES = "nameStrings.infraspecificEpithetSN";

  public static QMatcher getInstance(NameUsageRequest request) {
    if (request.isFuzzyMatchingEnabled()) {
      if (request.isWholeWordMatchingEnabled()) {
        return new FuzzyWholeWordQMatcher(request);
      }
      return new FuzzyPartialQMatcher(request);
    } else if (request.isWholeWordMatchingEnabled()) {
      return new WholeWordQMatcher(request);
    }
    return new PartialQMatcher(request);
  }

  final NameUsageRequest request;

  QMatcher(NameUsageRequest request) {
    this.request = request;
  }

  /*
   * Note about the boost values. You need to experiment, but given a max ngram size of 10 and given a search term of exactly that length, a
   * prefix query scores about as good as an autocomplete query if you let each letter increase the boost value by 0.1 for prefix queries,
   * while boosting autocomplete queries by 3.5. It's hard to arrive at watertight relevance scores though, because the effects of
   * TD/IF-scoring are impossible to estimate unless you also know your data really well.
   */

  public Query getVernacularNameQuery() {
    String q = request.getQ();
    return new DisMaxQuery()
        .subquery(new CaseInsensitivePrefixQuery("vernacularNames", q).withBoost(0.1 * q.length()))
        .subquery(new AutoCompleteQuery("vernacularNames", q).withOperator(AND).withBoost(3.5));
  }

  public Query getAuthorshipQuery() {
    String q = request.getQ();
    return new DisMaxQuery()
        .subquery(new CaseInsensitivePrefixQuery("authorship", q).withBoost(0.1 * q.length()))
        .subquery(new AutoCompleteQuery("authorship", q).withOperator(AND).withBoost(3.5));
  }

  public abstract Query getScientificNameQuery();

}
