package life.catalogue.es.nu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.dao.CatCopy;
import life.catalogue.es.ddl.MultiField;
import life.catalogue.es.query.CaseInsensitiveQuery;
import life.catalogue.es.query.DisMaxQuery;
import life.catalogue.es.query.EdgeNgramQuery;
import life.catalogue.es.query.Query;
import life.catalogue.es.query.SciNameEqualsQuery;
import life.catalogue.es.query.SciNameMatchQuery;
import life.catalogue.es.query.TermQuery;
import static life.catalogue.es.query.AbstractMatchQuery.Operator.*;

/**
 * Generates the queries for the suggest service and the search service. See also {@link MultiField}
 * for consideration about how & why.
 */
public abstract class QMatcher {

  static final int MIN_NGRAM_SIZE = 2; // see es-settings.json
  static final int MAX_NGRAM_SIZE = 10;

  private static final Logger LOG = LoggerFactory.getLogger(QMatcher.class);

  static final String FLD_SCINAME = "scientificName";
  static final String FLD_AUTHOR = "authorship";
  static final String FLD_VERNACULAR = "vernacularNames";
  static final String FLD_GENUS = "nameStrings.genusOrMonomial";
  static final String FLD_GENUS_LETTER = "nameStrings.genusLetter";
  static final String FLD_SPECIES = "nameStrings.specificEpithet";
  static final String FLD_SUBSPECIES = "nameStrings.infraspecificEpithet";

  public static QMatcher getInstance(NameUsageRequest request) {
    QMatcher matcher;
    switch (request.getSearchType()) {
      case EXACT:
        if (LOG.isTraceEnabled()) {
          LOG.trace("Instantiating EXACT matcher for search phrase \"{}\"", request.getQ());
        }
        matcher = getExactQMatcher(request);
        break;
      case PREFIX:
        if (request.isFuzzy()) {
          if (LOG.isTraceEnabled()) {
            LOG.trace("Instantiating FUZZY PREFIX matcher for search phrase \"{}\"", request.getQ());
          }
          matcher = new FuzzyPrefixMatcher(request);
        } else {
          if (LOG.isTraceEnabled()) {
            LOG.trace("Instantiating SIMPLE PREFIX matcher for search phrase \"{}\"", request.getQ());
          }
          matcher = new SimplePrefixMatcher(request);
        }
        break;
      case WHOLE_WORDS:
        if (request.isFuzzy()) {
          if (LOG.isTraceEnabled()) {
            LOG.trace("Instantiating FUZZY WHOLE_WORDS matcher for search phrase \"{}\"", request.getQ());
          }
          matcher = new FuzzyWholeWordMatcher(request);
        } else {
          if (LOG.isTraceEnabled()) {
            LOG.trace("Instantiating SIMPLE WHOLE_WORDS matcher for search phrase \"{}\"", request.getQ());
          }
          matcher = new SimpleWholeWordMatcher(request);
        }
        break;
      default:
        throw new AssertionError();
    }
    return matcher;
  }

  final NameUsageRequest request;

  QMatcher(NameUsageRequest request) {
    this.request = request;
  }

  /*
   * Note about the boost values. You need to experiment, but given a max ngram size of 10 and given a
   * search term of exactly that length, a prefix query seems to score about as good as an edge ngram
   * query if you let each letter increase the boost value by 0.1 for prefix queries, while boosting
   * autocomplete queries by 3.5. So the prefix query will catch up the ngram query by the time the
   * max ngram size is reached. It's hard to arrive at watertight relevance scores though, because the
   * effects of TD/IF-scoring are impossible to estimate unless you also know your data really well.
   */

  public Query getVernacularNameQuery() {
    String q = CatCopy.transLatin.transliterate(request.getQ().toLowerCase());
    return new DisMaxQuery()
        .subquery(new CaseInsensitiveQuery(FLD_VERNACULAR, q).withBoost(100.0))
        .subquery(new EdgeNgramQuery(FLD_VERNACULAR, q).withOperator(AND));

  }

  public Query getAuthorshipQuery() {
    String q = request.getQ().toLowerCase();
    return new DisMaxQuery()
        .subquery(new CaseInsensitiveQuery(FLD_AUTHOR, q).withBoost(100.0))
        .subquery(new EdgeNgramQuery(FLD_AUTHOR, q).withOperator(AND));
  }

  public Query getScientificNameQuery() {
    String[] terms = request.getSciNameSearchTerms();
    if (couldBeEpithets()) {
      if (terms.length == 1 && terms[0].length() > 2) { // Let's wait a bit before engaging this one
        return matchAsMonomial();
      } else if (terms.length == 2) {
        return matchAsBinomial();
      } else if (terms.length == 3) {
        return matchAsTrinomial();
      }
    }
    return sciNameBaseQuery();
  }

  abstract Query matchAsMonomial();

  abstract Query matchAsBinomial();

  abstract Query matchAsTrinomial();

  DisMaxQuery sciNameBaseQuery() {
    return new DisMaxQuery()
        // Make sure exact matches (even on small scientific names like genus "Ara") always prevail
        .subquery(new SciNameEqualsQuery(FLD_SCINAME, request.getQ()).withBoost(100.0))
        .subquery(new SciNameMatchQuery(FLD_SCINAME, request.getQ()));
  }

  private boolean couldBeEpithets() {
    String[] terms = request.getSciNameSearchTerms();
    if (terms == null) {
      return false;
    }
    if (terms.length > 3) {
      // could still all be epithets but we don't know how to deal with them any longer
      return false;
    } else if (terms.length == 1) {
      // With just one search term it's too much of a stretch to assume the user is typing the abbreviated
      // form of a generic epithet
      return couldBeEpithet(terms[0]);
    }
    for (String term : terms) {
      if (term.length() == 1 && isEpitheticalCharacter(term.codePointAt(0))) {
        continue; // User could be typing "H sapiens", which we cater for, or even "sapiens H", which we still cater
                  // for.
      } else if (term.length() == 2 && isEpitheticalCharacter(term.codePointAt(0)) && term.charAt(1) == '.') {
        continue; // User could be typing "H. sapiens"
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
    /*
     * In fact hyphen and apostophe are also legitimate characters in an epithet, but these are filtered
     * out by the tokenizer, so what remains is a strings of letters.
     */
    return Character.isLetter(i);
  }

  private static QMatcher getExactQMatcher(NameUsageRequest request) {
    return new QMatcher(request) {

      public Query getScientificNameQuery() {
        return new TermQuery(FLD_SCINAME, request.getQ());
      }

      Query matchAsTrinomial() {
        return null;
      }

      Query matchAsMonomial() {
        return null;
      }

      Query matchAsBinomial() {
        return null;
      }
    };
  }

}
