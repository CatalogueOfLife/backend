package life.catalogue.es.suggest;

import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.es.EsMonomial;
import life.catalogue.es.EsNameUsage;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.ListIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.core.search.Hit;

/**
 * Converts an ES Hit instance into a NameUsageSuggestion object.
 */
class SearchHitConverter {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SearchHitConverter.class);

  NameUsageSuggestion createSuggestion(Hit<EsNameUsage> hit) {
    EsNameUsage doc = hit.source();
    NameUsageSuggestion suggestion = new NameUsageSuggestion();
    suggestion.setScore(hit.score() != null ? hit.score().floatValue() : 0f);
    suggestion.setMatch(doc.getScientificName());
    suggestion.setUsageId(doc.getUsageId());
    suggestion.setNameId(doc.getNameId());
    suggestion.setGroup(doc.getGroup());
    suggestion.setNomCode(doc.getNomCode());
    suggestion.setRank(doc.getRank());
    suggestion.setStatus(doc.getStatus());
    if (doc.getStatus() != null && doc.getStatus().isSynonym()) { // a synonym
      suggestion.setAcceptedName(doc.getAcceptedName());
      if (doc.getClassificationIds() == null || doc.getClassificationIds().size() < 2) {
        // That's corrupt data but let's not make the suggestion service trip over it
        LOG.warn("Missing classification for synonym {} {}", doc.getScientificName(), doc.getUsageId());
      } else {
        // the first entry is the synonym itself, the 2nd the accepted name
        suggestion.setAcceptedUsageId(doc.getClassificationIds().get(doc.getClassificationIds().size() - 2));
      }
    }
    // figure out best classification context. Aim at family if possible
    if (doc.getClassification() == null) {
      // That's corrupt data but let's not make the suggestion service trip over it
      LOG.warn("Missing classification for {} {}", doc.getScientificName(), doc.getUsageId());
    } else if (doc.getClassification().size() > 1) { // not a kingdom
      EsMonomial parent = findContext(doc.getClassification());
      if (parent != null) {
        suggestion.setContext(parent.getName());
      }
    }
    return suggestion;
  }

  EsMonomial findContext(List<EsMonomial> classification) {
    EsMonomial best = null;
    // Iterate in reverse order, start from second last
    ListIterator<EsMonomial> li = classification.listIterator(classification.size()-1);
    while(li.hasPrevious()) {
      EsMonomial mono = li.previous();
      if (mono.getRank() == Rank.FAMILY) {
        return mono;
      }
      if (best == null) {
        best = mono;
      } else if (mono.getRank() != null && (
            best.getRank() == null ||
            Rank.GENUS.higherOrEqualsTo(best.getRank()) && mono.getRank().higherThan(Rank.GENUS)
      )) {
        best = mono;
      }
    }
    return best;
  }
}
