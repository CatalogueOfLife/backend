package life.catalogue.es.name;

import java.util.Arrays;

import life.catalogue.es.query.AutoCompleteQuery;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.CaseInsensitivePrefixQuery;
import life.catalogue.es.query.DisMaxQuery;
import life.catalogue.es.query.PrefixQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeWeakly;
import static life.catalogue.es.query.AbstractMatchQuery.Operator.*;



public class QTranslationUtils {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(QTranslationUtils.class);

  private static int MAX_NGRAM_SIZE = 10; // see es-settings.json

  private static final String SN_FIELD = "scientificName";
  private static final String GENUS_FIELD = "nameStrings.genusOrMonomialWN";
  private static final String SPECIES_FIELD = "nameStrings.specificEpithetSN";
  private static final String SUBSPECIES_FIELD = "nameStrings.infraspecificEpithetSN";

  private QTranslationUtils() {}

  public static Query getVernacularNameQuery(String q) {
    return matchSearchPhrase("vernacularNames", q);
  }

  public static Query getAuthorshipQuery(String q) {
    return matchSearchPhrase("authorship", q);
  }

  /**
   * Returns a scientific name query appropriate for the search phrase. The more unlikely the search phrase (e.g. specific
   * epithet followed by generic epithet), the lower the boost. We don't cater for really awkward search phrases (e.g.
   * genus in the middle, infraspecdific epithet to the left, specific epithet to the right).
   */
  public static Query getScientificNameQuery(String q) {
    String[] terms = tokenize(q);
    if (terms.length == 1) {
      return checkAllEpithets(terms);
    } else if (terms.length == 2) {
      return checkEpithetPairs(terms);
    } else if (terms.length == 3) {
      return checkEpithetTriplets(terms);
    }
    return matchSearchPhrase(SN_FIELD, q).withBoost(1.02); // Prefer over vernacular name
  }

  private static Query checkAllEpithets(String[] terms) {
    String termWN = normalizeWeakly(terms[0]);
    String termSN = normalizeStrongly(terms[0]);
    return new DisMaxQuery()
        .subquery(new BoolQuery() // Prefer subspecies over species and species over genera
            .should(matchSearchTerm(GENUS_FIELD, termWN).withBoost(1.02))
            .should(matchSearchTerm(SPECIES_FIELD, termSN).withBoost(1.05))
            .should(matchSearchTerm(SUBSPECIES_FIELD, termSN).withBoost(1.08)))
        .subquery(matchSearchPhrase(SN_FIELD, terms[0]).withBoost(0.8));
  }

  private static Query checkEpithetPairs(String[] terms) {
    String term0WN = normalizeWeakly(terms[0]);
    String term1WN = normalizeWeakly(terms[1]);
    String term0SN = normalizeStrongly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    return new DisMaxQuery()
        .subquery(new BoolQuery()
            .must(matchGenus(term0WN))
            .must(matchSearchTerm(SPECIES_FIELD, term1SN))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(matchGenus(term0WN))
            .must(matchSearchTerm(SUBSPECIES_FIELD, term1SN))
            .withBoost(1.6)) // We still like this combination better than the binomial when autocompleting
        .subquery(new BoolQuery()
            .must(matchSearchTerm(SPECIES_FIELD, term0SN))
            .must(matchSearchTerm(SUBSPECIES_FIELD, term1SN))
            .withBoost(1.2))
        .subquery(new BoolQuery()
            .must(matchSearchTerm(SUBSPECIES_FIELD, term0SN))
            .must(matchSearchTerm(SPECIES_FIELD, term1SN))
            .withBoost(1.15))
        .subquery(new BoolQuery() // The most out-there search phrase still supported: "sapiens H" or "sapiens Homo"
            .must(matchSearchTerm(SPECIES_FIELD, term0SN))
            .must(matchGenus(term1WN))
            .withBoost(1.1));
  }

  private static Query checkEpithetTriplets(String[] terms) {
    String term0WN = normalizeWeakly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    String term2SN = normalizeStrongly(terms[2]);
    return new DisMaxQuery()
        .subquery(new BoolQuery()
            .must(matchGenus(term0WN))
            .must(matchSearchTerm(SPECIES_FIELD, term1SN))
            .must(matchSearchTerm(SUBSPECIES_FIELD, term2SN))
            .withBoost(2.0))
        .subquery(new BoolQuery() // User mixed up specific/infraspecific epithets
            .must(matchGenus(term0WN))
            .must(matchSearchTerm(SUBSPECIES_FIELD, term1SN))
            .must(matchSearchTerm(SPECIES_FIELD, term2SN))
            .withBoost(1.8));
  }

  private static Query matchGenus(String term) {
    if (term.length() == 1 || (term.length() == 2 && term.charAt(1) == '.')) {
      return new TermQuery("nameStrings.genusLetter", term.charAt(0));
    }
    return matchSearchTerm(GENUS_FIELD, term);
  }

  private static Query matchSearchPhrase(String field, String q) {
    /*
     * If the user has typed one big search term we still try to help. With multiple big search terms the search/suggest
     * service just blanks out.
     */
    if (q.length() > MAX_NGRAM_SIZE && countTokens(q) == 1) {
      return new CaseInsensitivePrefixQuery(field, q);
    }
    return new AutoCompleteQuery(field, q).withOperator(AND);
  }

  private static Query matchSearchTerm(String field, String term) {
    if (term.length() > MAX_NGRAM_SIZE) {
      // Both the term and the fields against which it is matched are already in lower case, so a prefix query suffices.
      return new PrefixQuery(field, term);
    }
    return new AutoCompleteQuery(field, term);
  }

  private static String[] tokenize(String q) {
    return Arrays.stream(q.split("\\W")).filter(s -> !s.isEmpty()).toArray(String[]::new);
  }

  private static long countTokens(String q) {
    return Arrays.stream(q.split("\\W")).filter(s -> !s.isEmpty()).count();
  }

}
