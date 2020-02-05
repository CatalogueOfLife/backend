package life.catalogue.es.name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.es.mapping.MultiField;
import life.catalogue.es.query.AutoCompleteQuery;
import life.catalogue.es.query.BoolQuery;
import life.catalogue.es.query.CaseInsensitivePrefixQuery;
import life.catalogue.es.query.DisMaxQuery;
import life.catalogue.es.query.PrefixQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameAutoCompleteQuery;
import life.catalogue.es.query.TermQuery;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeWeakly;
import static life.catalogue.es.query.AbstractMatchQuery.Operator.AND;

/**
 * Generates the queries for the suggest service and the search service. See also {@link MultiField} for consideration about how & why.
 *
 */
public class QTranslationUtils {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(QTranslationUtils.class);

  private static final int MAX_NGRAM_SIZE = 10; // see es-settings.json

  /*
   * The boost for prefix queries. We strongly reduce the boost for prefix queries, even though these could produce perfect,
   * letter-for-letter matches. Prefix queries are non-scoring and therefore default to a score of 1.0, which is pretty high compared to
   * scores produced by the autocomplete queries. For vernacular name and author searches the prefix queries execute alongside the
   * autocomplete queries. For scientific name searches it's either a prefix query or an auto complete query. The prefix query only executes
   * if there's just one search term and its length exceeds MAX_NGRAM_SIZE. We know it is save to ditch it otherwise, because contrary to the
   * analyzers for vernacular names and authors, the analyzer for the scientific name field basically leaves its value intact. However, this
   * makes it a harder to compare scientific name search results with vernacular name or author search results. In practice this is not a
   * problem as long as we always give prefix queries a very low boost and then make it a "race" about the greatest number of common
   * characters. In that case it doesn't really matter whether the prefix query wins in dis_max queries or the autocomplete query.
   */
  private static final double PQBOOST = 0.01;

  private static final String FLD_SCINAME = "scientificName";
  private static final String FLD_GENUS = "nameStrings.genusOrMonomialWN";
  private static final String FLD_SPECIES = "nameStrings.specificEpithetSN";
  private static final String FLD_SUBSPECIES = "nameStrings.infraspecificEpithetSN";

  private QTranslationUtils() {}

  public static Query getVernacularNameQuery(String q) {
    return new DisMaxQuery()
        .subquery(new CaseInsensitivePrefixQuery("vernacularNames", q).withBoost(PQBOOST * q.length()))
        .subquery(new AutoCompleteQuery("vernacularNames", q).withOperator(AND));
  }

  public static Query getAuthorshipQuery(String q) {
    return new DisMaxQuery()
        .subquery(new CaseInsensitivePrefixQuery("authorship", q).withBoost(PQBOOST * q.length()))
        .subquery(new AutoCompleteQuery("authorship", q).withOperator(AND));
  }

  /**
   * Returns a scientific name query appropriate for the search phrase. Besides a regular ngram search, if the search phrase consist of one,
   * two or three words, we also try to interpret and match it as a monomial, binomial c.q. trinomial (i.e. we match against normalized
   * versions of the epithets).
   */
  public static Query getScientificNameQuery(String q, String[] terms) {
    if (couldBeEpithets(terms)) {
      if (terms.length == 1 && terms[0].length() > 2) { // Let's wait a bit before engaging this one
        return matchAsMonomial(q, terms);
      } else if (terms.length == 2) {
        return matchAsBinomial(q, terms);
      } else if (terms.length == 3) {
        return matchAsTrinomial(q, terms);
      }
    }
    return matchScientificName(q, terms);
  }

  private static Query matchAsMonomial(String q, String[] terms) {
    String termWN = normalizeWeakly(terms[0]);
    String termSN = normalizeStrongly(terms[0]);
    return new DisMaxQuery()
        .subquery(matchScientificName(q, terms)) // Always also match on full scientific name
        .subquery(new BoolQuery() // Prefer subspecies over species and species over genera
            .should(matchAsEpithet(FLD_GENUS, termWN).withBoost(1.02))
            .should(matchAsEpithet(FLD_SPECIES, termSN).withBoost(1.05))
            .should(matchAsEpithet(FLD_SUBSPECIES, termSN).withBoost(1.08)));
  }

  private static Query matchAsBinomial(String q, String[] terms) {
    String term0WN = normalizeWeakly(terms[0]);
    String term0SN = normalizeStrongly(terms[0]);
    String term1WN = normalizeWeakly(terms[1]);
    String term1SN = normalizeStrongly(terms[1]);
    return new DisMaxQuery()
        .subquery(matchScientificName(q, terms))
        .subquery(new BoolQuery() // interpret 1st term as genus, 2nd as infraspecific epithet
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .withBoost(1.6))
        .subquery(new BoolQuery() // interpret 1st term as genus, 2nd as specific epithet
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .withBoost(1.5))
        .subquery(new BoolQuery() // interpret 1st term as specific epithet, 2nd as infraspecific epithet
            .must(matchAsEpithet(FLD_SPECIES, term0SN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .withBoost(1.2))
        .subquery(new BoolQuery() // interpret 1st term as infraspecific epithet, 2nd as specific epithet
            .must(matchAsEpithet(FLD_SUBSPECIES, term0SN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .withBoost(1.15))
        .subquery(new BoolQuery() // catches sapiens H
            .must(matchAsEpithet(FLD_SPECIES, term0SN))
            .must(matchAsGenericEpithet(term1WN))
            .withBoost(1.10));
  }

  private static Query matchAsTrinomial(String q, String[] terms) {
    String term0WN = normalizeWeakly(terms[0]);
    String term1SN = normalizeStrongly(terms[1]);
    String term2SN = normalizeStrongly(terms[2]);
    return new DisMaxQuery()
        .subquery(matchScientificName(q, terms))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term2SN))
            .withBoost(2.0)) // bingo
        .subquery(new BoolQuery() // user mixed up specific/infraspecific epithets
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .must(matchAsEpithet(FLD_SPECIES, term2SN))
            .withBoost(1.8));
  }

  private static Query matchAsGenericEpithet(String term) {
    if (term.length() == 1 || (term.length() == 2 && term.charAt(1) == '.')) {
      return new TermQuery("nameStrings.genusLetter", term.charAt(0));
    }
    return matchAsEpithet(FLD_GENUS, term);
  }

  private static Query matchScientificName(String q, String[] terms) {
    if (terms.length == 1) { // then q == terms[0]
      if (terms[0].length() > MAX_NGRAM_SIZE) {
        return new CaseInsensitivePrefixQuery(FLD_SCINAME, terms[0]);
      }
    }
    return new SciNameAutoCompleteQuery(FLD_SCINAME, q).withOperator(AND).withBoost(PQBOOST * q.length());
  }

  private static Query matchAsEpithet(String field, String term) {
    if (term.length() > MAX_NGRAM_SIZE) {
      return new PrefixQuery(field, term).withBoost(PQBOOST * term.length());
    }
    return new SciNameAutoCompleteQuery(field, term).withOperator(AND).withBoost(PQBOOST * term.length());
  }

  private static boolean couldBeEpithets(String[] terms) {
    if (terms.length == 1) {
      // Then it makes no sense to assume is typing the abbreviated form of a generic epithet
      return couldBeEpithet(terms[0]);
    }
    for (String term : terms) {
      if (term.length() == 1 && isEpitheticalCharacter(term.codePointAt(0))) {
        // User could be typing a binomial (or trinomial) like "H Sapiens", which we cater for
        continue;
      }
      if (term.length() == 2 && isEpitheticalCharacter(term.codePointAt(0)) && term.charAt(1) == '.') {
        // User could be typing a binomial (or trinomial) like "H. Sapiens"
        continue;
      }
      if (!couldBeEpithet(term)) {
        return false;
      }
    }
    return true;
  }

  private static boolean couldBeEpithet(String term) {
    return term.chars().filter(i -> !isEpitheticalCharacter(i)).findFirst().isEmpty();
  }

  private static boolean isEpitheticalCharacter(int i) {
    return Character.isLetterOrDigit(i) || (char) i == '-' || (char) i == '\'';
  }

}
