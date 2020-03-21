package life.catalogue.es.nu.suggest;

import java.util.List;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.response.SearchHit;

class SuggestionFactory {

  private final VernacularNameMatcher matcher;

  SuggestionFactory(NameUsageSuggestRequest request) {
    if (request.suggestVernaculars()) {
      this.matcher = new VernacularNameMatcher(request);
    } else {
      // matcher is not going to be used
      this.matcher = null;
    }
  }

  NameUsageSuggestion createSuggestion(SearchHit<EsNameUsage> hit, boolean isVernacularName) {
    NameUsageSuggestion suggestion = new NameUsageSuggestion();
    suggestion.setScore(hit.getScore());
    suggestion.setVernacularName(isVernacularName);
    EsNameUsage doc = hit.getSource();
    if (isVernacularName) {
      List<String> names = hit.getSource().getVernacularNames();
      suggestion.setMatch(matcher.getMatch(names));
    } else {
      suggestion.setMatch(doc.getScientificName());
    }
    if (doc.getStatus() != null && doc.getStatus().isSynonym()) {
      suggestion.setAcceptedName(doc.getAcceptedName());
    }
    suggestion.setUsageId(doc.getUsageId());
    suggestion.setNomCode(doc.getNomCode());
    suggestion.setRank(doc.getRank());
    suggestion.setStatus(doc.getStatus());
    return suggestion;
  }

}
