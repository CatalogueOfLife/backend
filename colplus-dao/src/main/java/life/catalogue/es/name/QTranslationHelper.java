package life.catalogue.es.name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.search.NameUsageRequest;
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
 */
public class QTranslationHelper {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(QTranslationHelper.class);

  private static final int MAX_NGRAM_SIZE = 10; // see es-settings.json

  private static final String FLD_SCINAME = "scientificName";
  private static final String FLD_GENUS = "nameStrings.genusOrMonomialWN";
  private static final String FLD_SPECIES = "nameStrings.specificEpithetSN";
  private static final String FLD_SUBSPECIES = "nameStrings.infraspecificEpithetSN";

  private final NameUsageRequest request;

  public QTranslationHelper(NameUsageRequest request) {
    this.request = request;
  }

  /*
   * Note about the boost values. Elasticsearch's scoring mechanism is opaque, but, given a max ngram size of 10 and given a search term of
   * exactly that length, a prefix query scores about as good as an autocomplete query if you let each letter increase the boost value by
   * 0.1 for prefix queries, while boosting autocomplete queries by 3.5. It's hard to arrive at watertight relevance scores though, because
   * the effects of TD/IF-scoring are impossible to estimate unless you also know your data really well.
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

  /**
   * Returns a scientific name query appropriate for the search phrase. Besides a regular ngram search, if the search phrase consist of one,
   * two or three words, we also try to interpret and match it as a monomial, binomial c.q. trinomial (i.e. we match against normalized
   * versions of the epithets).
   */
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
    return matchScientificName().withBoost(1.5); // Bump above vernacular names
  }

  private Query matchAsMonomial() {
    String[] terms = request.getSearchTerms();
    String termWN = normalizeWeakly(terms[0]);
    String termSN = normalizeStrongly(terms[0]);
    /*
     * Even if search consists of a single term, we still need to match against the scientific name in order to catch unparsed names like
     * "MV-L51 ICTV"
     */
    return new DisMaxQuery()
        .subquery(matchScientificName().withBoost(4.0))
        .subquery(new BoolQuery() // Prefer subspecies over species and species over genera
            .should(matchAsEpithet(FLD_SUBSPECIES, termSN).withBoost(1.2))
            .should(matchAsEpithet(FLD_SPECIES, termSN).withBoost(1.1))
            .should(matchAsEpithet(FLD_GENUS, termWN).withBoost(1.0))
            .withBoost(4.0));
  }

  private Query matchAsBinomial() {
    String[] terms = request.getSearchTerms();
    String term0WN = normalizeWeakly(terms[0]);
    String term0SN = normalizeStrongly(terms[0]);
    String term1WN = normalizeWeakly(terms[1]);
    String term1SN = normalizeStrongly(terms[1]);
    return new DisMaxQuery()
        .subquery(matchScientificName().withBoost(2.0))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .withBoost(1.4))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .withBoost(1.3))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SPECIES, term0SN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .withBoost(1.2))
        .subquery(new BoolQuery()
            .must(matchAsEpithet(FLD_SUBSPECIES, term0SN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .withBoost(1.1))
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
    return new DisMaxQuery()
        .subquery(matchScientificName().withBoost(2.0))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SPECIES, term1SN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term2SN))
            .withBoost(1.5))
        .subquery(new BoolQuery()
            .must(matchAsGenericEpithet(term0WN))
            .must(matchAsEpithet(FLD_SUBSPECIES, term1SN))
            .must(matchAsEpithet(FLD_SPECIES, term2SN))
            .withBoost(1.4));
  }

  private static Query matchAsGenericEpithet(String term) {
    if (term.length() == 1) {
      return new TermQuery("nameStrings.genusLetter", term.charAt(0)).withBoost(0.2); // Nice but not great
    } else if (term.length() == 2 && term.charAt(1) == '.') {
      return new TermQuery("nameStrings.genusLetter", term.charAt(0)).withBoost(0.4); // More ominous
    }
    return matchAsEpithet(FLD_GENUS, term);
  }

  private Query matchScientificName() {
    String q = request.getQ();
    String[] terms = request.getSearchTerms();
    if (terms.length == 1) { // q == terms[0]
      if (terms[0].length() > MAX_NGRAM_SIZE) {
        return new CaseInsensitivePrefixQuery(FLD_SCINAME, q).withBoost(0.1 * q.length());
      }
    }
    return new SciNameAutoCompleteQuery(FLD_SCINAME, q).withOperator(AND).withBoost(3.5);
  }

  private static Query matchAsEpithet(String field, String term) {
    if (term.length() > MAX_NGRAM_SIZE) {
      return new PrefixQuery(field, term).withBoost(0.1 * term.length());
    }
    return new SciNameAutoCompleteQuery(field, term).withOperator(AND).withBoost(3.5);
  }

  private boolean couldBeEpithets() {
    String[] terms = request.getSearchTerms();
    if (terms.length == 1) {
      // Then one or two characters is not enough to assume the user is typing the abbreviated form of a generic epithet
      return couldBeEpithet(terms[0]);
    }
    for (String term : terms) {
      if (term.length() == 1 && isEpitheticalCharacter(term.codePointAt(0))) {
        continue; // User could be typing a binomial like "H Sapiens", which we cater for
      } else if (term.length() == 2 && isEpitheticalCharacter(term.codePointAt(0)) && term.charAt(1) == '.') {
        continue; // User could be typing a binomial (or trinomial) like "H. Sapiens"
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
    return Character.isLetterOrDigit(i) || (char) i == '-' || (char) i == '\'';
  }

}
