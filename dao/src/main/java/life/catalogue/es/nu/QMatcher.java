package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * Generates the queries for the suggest service and the search service.
 */
public abstract class QMatcher {

  static final int MIN_NGRAM_SIZE = 2; // see schema.json
  static final int MAX_NGRAM_SIZE = 10;

  private static final Logger LOG = LoggerFactory.getLogger(QMatcher.class);

  static final String FLD_SCINAME = "scientificName";
  static final String FLD_AUTHOR = "authorshipComplete";
  static final String FLD_VERNACULAR = "vernacularNames";
  static final String FLD_GENUS = "nameStrings.genusOrMonomial";
  static final String FLD_GENUS_LETTER = "nameStrings.genusLetter";
  static final String FLD_INFRAGENERIC = "nameStrings.infragenericEpithet";
  static final String FLD_SPECIES = "nameStrings.specificEpithet";
  static final String FLD_SUBSPECIES = "nameStrings.infraspecificEpithet";

  public static QMatcher getInstance(NameUsageRequest request) {
    QMatcher matcher;
    // default to WHOLE_WORDS
    switch (ObjectUtils.coalesce(request.getSearchType(), NameUsageRequest.SearchType.WHOLE_WORDS)) {
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

  public Query getAuthorshipQuery() {
    String q = request.getQ().toLowerCase();
    return Query.of(qb -> qb.match(m -> m.field(FLD_AUTHOR + ".std").query(q)));
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

  /**
   * Returns the base scientific name subqueries: an exact match (case-insensitive) and a whole-word match.
   * Subclasses add epithet-based queries and combine everything into a disMax.
   */
  List<Query> sciNameBaseQueries() {
    String q = request.getQ();
    List<Query> queries = new ArrayList<>();
    // Make sure exact matches (even on small scientific names like genus "Ara") always prevail
    queries.add(Query.of(qb -> qb.match(m -> m.field(FLD_SCINAME + ".sic").query(q).boost(100.0f))));
    queries.add(Query.of(qb -> qb.match(m -> m.field(FLD_SCINAME + ".sww").query(q))));
    return queries;
  }

  /**
   * Default scientific name query when no epithet-based matching is possible.
   */
  Query sciNameBaseQuery() {
    List<Query> queries = sciNameBaseQueries();
    return Query.of(q -> q.disMax(d -> d.queries(queries)));
  }

  /**
   * Wraps a query with an explicit boost value.
   */
  public static Query withBoost(Query query, float boost) {
    return Query.of(q -> q.bool(b -> b.must(query).boost(boost)));
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
        return Query.of(q -> q.term(t -> t.field(FLD_SCINAME).value(request.getQ())));
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
