package org.col.es.name;

import org.apache.commons.lang3.StringUtils;
import org.col.es.dsl.AutoCompleteQuery;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.DisMaxQuery;
import org.col.es.dsl.Query;
import org.col.es.dsl.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.name.NameUsageWrapperConverter.normalizeStrongly;
import static org.col.es.name.NameUsageWrapperConverter.normalizeWeakly;

public class QTranslationUtils {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(QTranslationUtils.class);

  private static final String GENUS_FIELD = "nameStrings.genusOrMonomialWN";
  private static final String SPECIES_FIELD = "nameStrings.specificEpithetSN";
  private static final String SUBSPECIES_FIELD = "nameStrings.infraspecificEpithetSN";

  private QTranslationUtils() {}

  public static Query getVernacularNameQuery(String q) {
    return compare("vernacularNames", q);
  }

  public static Query getAuthorshipQuery(String q) {
    return compare("authorship", q);
  }

  /**
   * Returns a scientific name query appropriate for the search phrase. The more unlikely the search phrase (e.g. specific
   * epithet followed by generic epithet), the lower the boost. We don't cater for really awkward search phrases (e.g.
   * infraspecific epithet followed by generic epithet followed by specific epithet).
   */
  public static Query getScientificNameQuery(String q) {
    String[] terms = StringUtils.split(q);
    if (terms.length == 1) {
      return checkAllEpithets(terms);
    } else if (terms.length == 2) {
      return checkEpithetPairs(terms);
    } else if (terms.length == 3) {
      return checkEpithetTriplets(terms);
    }
    return getBasicSciNameQuery(normalizeWeakly(q));
  }

  private static Query checkAllEpithets(String[] terms) {
    String termWN = normalizeWeakly(terms[0]);
    String termSN = normalizeStrongly(terms[0]);
    // Slightly bump lower ranks up the list
    return new BoolQuery()
        .should(compare(GENUS_FIELD, termWN))
        .should(compare(SPECIES_FIELD, termSN).withBoost(1.05))
        .should(compare(SUBSPECIES_FIELD, termSN).withBoost(1.08));
  }

  private static Query checkEpithetPairs(String[] terms) {
    String term0WN = normalizeWeakly(terms[0]);
    String term1WN = normalizeWeakly(terms[1]);
    String term0SN = normalizeStrongly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    return new DisMaxQuery()
        .subquery(new BoolQuery()
            .must(getGenusOrMonomialQuery(term0WN))
            .must(compare(SPECIES_FIELD, term1SN))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(getGenusOrMonomialQuery(term0WN))
            .must(compare(SUBSPECIES_FIELD, term1SN))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(compare(SPECIES_FIELD, term0SN))
            .must(compare(SUBSPECIES_FIELD, term1SN))
            .withBoost(1.2))
        .subquery(new BoolQuery()
            .must(compare(SUBSPECIES_FIELD, term0SN))
            .must(compare(SPECIES_FIELD, term1SN))
            .withBoost(1.15))
        .subquery(new BoolQuery() // "Sapiens H." if you must
            .must(compare(SPECIES_FIELD, term0SN))
            .must(getGenusOrMonomialQuery(term1WN))
            .withBoost(1.1));
  }

  private static Query checkEpithetTriplets(String[] terms) {
    String term0WN = normalizeWeakly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    String term2SN = normalizeStrongly(terms[2]);
    return new DisMaxQuery()
        .subquery(new BoolQuery()
            .must(getGenusOrMonomialQuery(term0WN))
            .must(compare(SPECIES_FIELD, term1SN))
            .must(compare(SUBSPECIES_FIELD, term2SN))
            .withBoost(2.0))
        .subquery(new BoolQuery() // User forgot which was which
            .must(getGenusOrMonomialQuery(term0WN))
            .must(compare(SUBSPECIES_FIELD, term1SN))
            .must(compare(SPECIES_FIELD, term2SN))
            .withBoost(1.8));
  }

  private static Query getGenusOrMonomialQuery(String term) {
    if (term.length() == 1 || (term.length() == 2 && term.charAt(1) == '.')) {
      return new TermQuery("nameStrings.genusLetter", term.charAt(0));
    }
    return compare(GENUS_FIELD, term);
  }

  private static Query getBasicSciNameQuery(String q) {
    return compare("scientificName", q);
  }

  private static Query compare(String field, String value) {
    return new AutoCompleteQuery(field, value);
  }

}
