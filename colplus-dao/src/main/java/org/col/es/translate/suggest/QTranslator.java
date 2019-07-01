package org.col.es.translate.suggest;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Name;
import org.col.api.search.NameSuggestRequest;
import org.col.es.model.SearchableNameStrings;
import org.col.es.query.AutoCompleteQuery;
import org.col.es.query.DisMaxQuery;
import org.col.es.query.Query;

class QTranslator {

  private final NameSuggestRequest request;

  QTranslator(NameSuggestRequest request) {
    this.request = request;
  }

  /*
   * NB all boost values are wild guesses. Should probably make them configurable.
   */
  Query translate() {
    String[] words = Arrays.stream(StringUtils.split(request.getQ().trim(), ' ')).map(String::toLowerCase).toArray(String[]::new);
    if (words.length == 1) {
      String word = words[0];
      Name name = new Name();
      name.setGenus(word);
      name.setSpecificEpithet(word);
      name.setInfraspecificEpithet(word);
      SearchableNameStrings sns = new SearchableNameStrings(name);
      return new DisMaxQuery()
          .subquery(new AutoCompleteQuery("nameStrings.genus", sns.getGenus(), 50))
          .subquery(new AutoCompleteQuery("nameStrings.genusWN", sns.getGenusWN(), 40))
          .subquery(new AutoCompleteQuery("nameStrings.specificEpithet", sns.getSpecificEpithet(), 55))
          .subquery(new AutoCompleteQuery("nameStrings.specificEpithetSN", sns.getSpecificEpithetSN(), 45))
          .subquery(new AutoCompleteQuery("nameStrings.infraspecificEpithet", sns.getInfraspecificEpithet(), 55))
          .subquery(new AutoCompleteQuery("nameStrings.infraspecificEpithetSN", sns.getInfraspecificEpithetSN(), 45));
    }
    return null;
  }

}
