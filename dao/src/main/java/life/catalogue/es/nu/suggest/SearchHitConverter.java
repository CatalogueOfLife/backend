package life.catalogue.es.nu.suggest;

import java.util.List;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.UpwardConverter;
import life.catalogue.es.response.SearchHit;

/**
 * Converts an ES SearchHit instance into a NameUsageSuggestion object.
 */
class SearchHitConverter implements UpwardConverter<SearchHit<EsNameUsage>, NameUsageSuggestion> {

  private final VernacularNameMatcher matcher;

  SearchHitConverter(NameUsageSuggestRequest request) {
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
    if (doc.getAcceptedName() != null) {
      suggestion.setAcceptedName(doc.getAcceptedName());
    } else {
      suggestion.setAcceptedName(doc.getScientificName());
    }
    suggestion.setUsageId(doc.getUsageId());
    suggestion.setNomCode(doc.getNomCode());
    suggestion.setRank(doc.getRank());
    suggestion.setStatus(doc.getStatus());
    return suggestion;
  }

}
