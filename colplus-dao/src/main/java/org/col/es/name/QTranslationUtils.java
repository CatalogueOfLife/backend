package org.col.es.name;

import org.col.es.dsl.AutoCompleteQuery;
import org.col.es.dsl.BoolQuery;
import org.col.es.dsl.CaseInsensitivePrefixQuery;
import org.col.es.dsl.DisMaxQuery;
import org.col.es.dsl.Query;
import org.col.es.dsl.TermQuery;
import org.col.es.model.NameStrings;

import static org.col.es.model.NameStrings.tokenize;

public class QTranslationUtils {

  public static final float BASE_BOOST = 100;

  private static final int MAX_NGRAM_SIZE = 10; // see es-settings.json

  private QTranslationUtils() {}

  public static Query getVernacularNameQuery(String q) {
    return compare("vernacularNames", q).withBoost(BASE_BOOST);
  }

  public static Query getAuthorshipQuery(String q) {
    return compare("authorship", q).withBoost(BASE_BOOST);
  }

  public static Query getScientificNameQuery(String q, NameStrings strings) {
    Query simple = compare("nameStrings.scientificNameWN", q);
    Query advanced;
    int numTerms = tokenize(q).length;
    if (numTerms == 1) {
      /*
       * Compare the search phrase with genus, specific epither and infraspecific epithet. We slightly bump matches on
       * specific and infraspecific epithets. We also slight bump the query as a whole because all else being equals we'd
       * rather suggest scientific names than vernacular names or authors.
       */
      advanced = new BoolQuery()
          .should(getGenusQuery(strings))
          .should(getSpecificEpithetQuery(strings).withBoost(BASE_BOOST * 1.01F))
          .should(getInfraspecificEpithetQuery(strings).withBoost(BASE_BOOST * 1.02F))
          .withBoost(BASE_BOOST * 1.01F);
    } else if (numTerms == 2) {
      /*
       * match 1st term against genus and 2nd against either specific or infraspecific epithet. Slight bump matches on
       * infraspecific epithets.
       */
      advanced = new BoolQuery()
          .must(getGenusQuery(strings))
          .must(new BoolQuery()
              .should(getSpecificEpithetQuery(strings))
              .should(getInfraspecificEpithetQuery(strings).withBoost(BASE_BOOST * 1.01F))
              .minimumShouldMatch(1))
          .withBoost(BASE_BOOST * 1.2F);
    } else if (numTerms == 3) {
      advanced = new BoolQuery()
          .must(getGenusQuery(strings))
          .must(getSpecificEpithetQuery(strings))
          .must(getInfraspecificEpithetQuery(strings))
          .withBoost(BASE_BOOST * 1.5F); // that's almost guaranteed to be bingo
    } else {
      advanced = null;
    }
    if (advanced == null) {
      return simple;
    }
    return new DisMaxQuery().subquery(simple).subquery(advanced);
  }

  private static Query getGenusQuery(NameStrings strings) {
    if (strings.getGenus().length() == 1) {
      return new TermQuery("nameStrings.genusLetter", strings.getGenusLetter());
    }
    if (strings.getGenusWN() == null) { // normalized version does not differ from the original string
      return compare("nameStrings.genus", strings.getGenus());
    }
    return compare("nameStrings.genusWN", strings.getGenusWN());
  }

  private static Query getSpecificEpithetQuery(NameStrings strings) {
    if (strings.getSpecificEpithetSN() == null) {
      return compare("nameStrings.specificEpithet", strings.getSpecificEpithet());
    }
    return compare("nameStrings.specificEpithetSN", strings.getSpecificEpithetSN());
  }

  private static Query getInfraspecificEpithetQuery(NameStrings strings) {
    if (strings.getInfraspecificEpithetSN() == null) {
      return compare("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet());
    }
    return compare("nameStrings.infraspecificEpithetSN", strings.getInfraspecificEpithetSN());
  }

  /*
   * Only use if the field is actually analyzed using the IGNORE_CASE and the AUTOCOMLETE analyzers.
   */
  private static Query compare(String field, String value) {
    if (value.length() > MAX_NGRAM_SIZE) {
      return new CaseInsensitivePrefixQuery(field, value);
    }
    return new AutoCompleteQuery(field, value);
  }

}
