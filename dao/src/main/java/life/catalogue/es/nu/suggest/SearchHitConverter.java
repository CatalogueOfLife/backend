package life.catalogue.es.nu.suggest;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsMonomial;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.UpwardConverter;
import life.catalogue.es.nu.NameUsageWrapperConverter;
import life.catalogue.es.response.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Converts an ES SearchHit instance into a NameUsageSuggestion object.
 */
class SearchHitConverter implements UpwardConverter<SearchHit<EsNameUsage>, NameUsageSuggestion> {
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
      try {
        // we need the payload to figure out the acceptedUsageID
        String payload = hit.getSource().getPayload();
        if (payload != null) {
          NameUsageWrapper nuw = NameUsageWrapperConverter.inflate(payload);
          NameUsageBase syn = (NameUsageBase) nuw.getUsage();
          suggestion.setAcceptedUsageId(syn.getParentId());
        }
      } catch (IOException e) {
        LOG.error("Failed to inflate payload for synonym {}", doc.getUsageId(), e);
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
