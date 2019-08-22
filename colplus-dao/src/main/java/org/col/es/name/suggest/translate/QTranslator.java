package org.col.es.name.suggest.translate;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Name;
import org.col.api.search.NameSuggestRequest;
import org.col.es.name.NameStrings;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.DisMaxQuery;
import org.col.es.query.PrefixQuery;
import org.col.es.query.Query;
import static org.col.es.name.NameStrings.*;

class QTranslator {

  private static int MAX_NGRAM_SIZE = 10; // see es-settings.json

  private final NameSuggestRequest request;
  private final NameStrings strings;

  QTranslator(NameSuggestRequest request) {
    this.request = request;
    this.strings = new NameStrings(request.getQ().trim());
  }

  Query translate() {
    // if(request.isSimple()) {
    // if(request.isVernaculars()) {
    // return new BoolQuery()
    // .should(getScientificNameQuery())
    // .should(createQuery("vernacularNames",))
    // }
    // }
    return null;
  }

  private Query getScientificNameQuery() {
    Query simpleQuery = compare("nameStrings.scientificNameWN", strings.getScientificNameWN(), 1);
    if (request.isSimple()) {
      return simpleQuery;
    }
    Query advancedQuery = getAdvancedQuery();
    if (advancedQuery == null) {
      return simpleQuery;
    }
    // Even when using the "advanced" query mechanism, we still combine it with the simple query mechanism. The simple query is likely to
    // yield more results, and also more false positives, so we give the advanced query mechanism a significant boost compare to the simple
    // query mechanism.
    return new DisMaxQuery().subquery(advancedQuery).subquery(simpleQuery);
  }

  private Query getAdvancedQuery() {
    switch (tokenize(request.getQ()).length) {
      case 1:
        return new BoolQuery()
            .should(getGenusQuery())
            .should(getSpecificEpithetQuery())
            .should(getInfraspecificEpithetQuery());
      case 2:
        return new BoolQuery()
            .must(getGenusQuery())
            .must(new BoolQuery()
                .should(getSpecificEpithetQuery())
                .should(getInfraspecificEpithetQuery()));
      case 3:
        return new BoolQuery()
            .must(getGenusQuery())
            .must(getSpecificEpithetQuery())
            .must(getInfraspecificEpithetQuery());
      default:
        return null;
    }
  }

  private Query getGenusQuery() {
    if (strings.getGenusWN() == null) {
      return compare("nameStrings.genus", strings.getGenus(), 50);
    }
    return new DisMaxQuery()
        .subquery(compare("nameStrings.genus", strings.getGenus(), 50))
        .subquery(compare("nameStrings.genusWN", strings.getGenusWN(), 40));
  }

  private Query getSpecificEpithetQuery() {
    if (strings.getSpecificEpithetSN() == null) {
      return compare("nameStrings.specificEpithet", strings.getSpecificEpithet(), 50);
    }
    return new DisMaxQuery()
        .subquery(compare("nameStrings.specificEpithet", strings.getSpecificEpithet(), 50))
        .subquery(compare("nameStrings.specificEpithetSN", strings.getSpecificEpithetSN(), 40));
  }

  private Query getInfraspecificEpithetQuery() {
    if (strings.getInfraspecificEpithetSN() == null) {
      return compare("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet(), 50);
    }
    return new DisMaxQuery()
        .subquery(compare("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet(), 50))
        .subquery(compare("nameStrings.infraspecificEpithetSN", strings.getInfraspecificEpithetSN(), 40));
  }

  private static Query compare(String field, String value, float boost) {
    if (value.length() > MAX_NGRAM_SIZE) {
      return new PrefixQuery(field, value, boost);
    }
    return new AutoCompleteQuery(field, value, boost);
  }

}
