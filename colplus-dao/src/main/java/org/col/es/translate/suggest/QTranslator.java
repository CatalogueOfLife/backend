package org.col.es.translate.suggest;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Name;
import org.col.api.search.NameSuggestRequest;
import org.col.es.model.NameStrings;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.BoolQuery;
import org.col.es.query.DisMaxQuery;
import org.col.es.query.Query;
import org.col.es.query.TermQuery;

class QTranslator {

  private final NameSuggestRequest request;
  private final String[] words;
  private final NameStrings strings;

  QTranslator(NameSuggestRequest request) {
    this.request = request;
    this.words = StringUtils.split(request.getQ().trim(), ' ');
    this.strings=createNameStrings(words);
  }

  Query translate() {
    if (words.length == 1) {
      return new BoolQuery()
          .should(getGenusQuery())
          .should(getSpecificEpithetQuery())
          .should(getInfraspecificEpithetQuery());
    }
    if (words.length == 2) {
      return new BoolQuery()
          .must(getGenusQuery())
          .must(new BoolQuery()
              .should(getSpecificEpithetQuery())
              .should(getInfraspecificEpithetQuery()));
    }
    if (words.length == 3) {
      return new BoolQuery()
          .must(getGenusQuery())
          .must(getSpecificEpithetQuery())
          .must(getInfraspecificEpithetQuery());
    }
    return null;
  }

  private Query getGenusQuery() {
    if (strings.getGenus().length() == 1) {
      return new TermQuery("nameStrings.genusLetter", strings.getGenusLetter());
    }
    if (strings.getGenusWN() == null) {
      return new AutoCompleteQuery("nameStrings.genus", strings.getGenus(), 50);
    }
    return new DisMaxQuery()
        .subquery(new AutoCompleteQuery("nameStrings.genus", strings.getGenus(), 50))
        .subquery(new AutoCompleteQuery("nameStrings.genusWN", strings.getGenusWN(), 40));
  }

  private Query getSpecificEpithetQuery() {
    if (strings.getSpecificEpithetSN() == null) {
      return new AutoCompleteQuery("nameStrings.specificEpithet", strings.getSpecificEpithet(), 50);
    }
    return new DisMaxQuery()
        .subquery(new AutoCompleteQuery("nameStrings.specificEpithet", strings.getSpecificEpithet(), 50))
        .subquery(new AutoCompleteQuery("nameStrings.specificEpithetSN", strings.getSpecificEpithetSN(), 40));
  }

  private Query getInfraspecificEpithetQuery() {
    if (strings.getInfraspecificEpithetSN() == null) {
      return new AutoCompleteQuery("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet(), 50);
    }
    return new DisMaxQuery()
        .subquery(new AutoCompleteQuery("nameStrings.infraspecificEpithet", strings.getInfraspecificEpithet(), 50))
        .subquery(new AutoCompleteQuery("nameStrings.infraspecificEpithetSN", strings.getInfraspecificEpithetSN(), 40));
  }

  private static NameStrings createNameStrings(String[] words) {
    Name name = new Name();
    switch (words.length) {
      case 1:
        name.setGenus(words[0]);
        name.setSpecificEpithet(words[0]);
        name.setInfraspecificEpithet(words[0]);
        // Looks odd but that's how we will try to match the search phrase to a name.
        break;
      case 2:
        if (words[0].length() == 2 && words[0].charAt(1) == '.') {
          // Handle search phrase like "H. Sapiens"
          name.setGenus(String.valueOf(words[0].charAt(0)));
        } else {
          name.setGenus(words[0]);
        }
        name.setSpecificEpithet(words[1]);
        name.setInfraspecificEpithet(words[1]);
        break;
      case 3:
        if (words[0].length() == 2 && words[0].charAt(1) == '.') {
          // Handle search phrase like "H. Sapiens"
          name.setGenus(String.valueOf(words[0].charAt(0)));
        } else {
          name.setGenus(words[0]);
        }
        name.setSpecificEpithet(words[1]);
        name.setInfraspecificEpithet(words[2]);
      default:
        // TODO
    }
    return new NameStrings(name);
  }

}
