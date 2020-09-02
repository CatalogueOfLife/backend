package life.catalogue.es.nu.suggest;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.EsMonomial;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.UpwardConverter;
import life.catalogue.es.response.SearchHit;

/**
 * Converts an ES SearchHit instance into a NameUsageSuggestion object.
 */
class SearchHitConverter implements UpwardConverter<SearchHit<EsNameUsage>, NameUsageSuggestion> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SearchHitConverter.class);

  private final VernacularNameMatcher matcher;

  SearchHitConverter(NameUsageSuggestRequest request) {
    if (request.isVernaculars()) {
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
      suggestion.setParentOrAcceptedName(doc.getScientificName());
    } else if (doc.getAcceptedName() != null) { // *then* this is a synonym
      suggestion.setMatch(doc.getScientificName());
      suggestion.setParentOrAcceptedName(doc.getAcceptedName());
      if (doc.getClassificationIds() == null || doc.getClassificationIds().size() < 2) {
        // That's corrupt data but let's not make the suggestion service trip over it
        suggestion.setAcceptedUsageId(doc.getClassificationIds().get(doc.getClassificationIds().size() - 2));
      }
    } else {
      suggestion.setMatch(doc.getScientificName());
      if (doc.getClassification() == null) {
        // That's corrupt data but let's not make the suggestion service trip over it
        suggestion.setParentOrAcceptedName("MISSING PARENT TAXON");
      } else if (doc.getClassification().size() > 1) { // not a kingdom
        EsMonomial parent = doc.getClassification().get(doc.getClassification().size() - 2);
        suggestion.setParentOrAcceptedName(parent.getName());
      }
    }
    suggestion.setUsageId(doc.getUsageId());
    suggestion.setNomCode(doc.getNomCode());
    suggestion.setRank(doc.getRank());
    suggestion.setStatus(doc.getStatus());
    return suggestion;
  }

}
