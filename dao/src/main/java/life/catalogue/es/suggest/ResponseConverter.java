package life.catalogue.es.suggest;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.api.search.NameUsageWrapper;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

/**
 * Converts the Elasticsearch response to a NameSearchResponse instance.
 */
class ResponseConverter {
  private static final Logger LOG = LoggerFactory.getLogger(ResponseConverter.class);

  private final SearchResponse<NameUsageWrapper> esResponse;

  ResponseConverter(SearchResponse<NameUsageWrapper> esResponse) {
    this.esResponse = esResponse;
  }

  List<NameUsageSuggestion> convertEsResponse() throws IOException {
    return esResponse.hits().hits().stream()
      .map(this::createSuggestion)
      .filter(Objects::nonNull)
      .toList();
  }

  NameUsageSuggestion createSuggestion(Hit<NameUsageWrapper> hit) {
    var nuw = hit.source();
    if (nuw.getUsage() == null) {
      return null;
    }
    NameUsageSuggestion suggestion = new NameUsageSuggestion();
    suggestion.setScore(hit.score() == null ? 0 : hit.score().floatValue());
    suggestion.setUsageId(nuw.getId());
    suggestion.setGroup(nuw.getGroup());
    var u = nuw.getUsage();
    suggestion.setMatch(u.getLabel());
    suggestion.setStatus(u.getStatus());
    var n = u.getName();
    suggestion.setNameId(n.getId());
    suggestion.setNomCode(n.getCode());
    suggestion.setRank(n.getRank());
    if (u.getStatus() != null && u.getStatus().isSynonym()) { // a synonym
      var acc = nuw.getParent();
      suggestion.setAcceptedName(acc.getLabel());
      suggestion.setAcceptedUsageId(acc.getId());
    }
    // figure out best classification context. Aim at family if possible
    if (nuw.getClassification() == null) {
      // That's corrupt data but let's not make the suggestion service trip over it
      LOG.warn("Missing classification for {} {}", u.getLabel(), nuw.getId());
    } else if (nuw.getClassification().size() > 1) { // classification starts with oneself, so this makes sure we have at least some parent
      var ctxt = findContext(nuw.getClassification());
      if (ctxt != null) {
        suggestion.setContext(ctxt.getName());
      }
    }
    return suggestion;
  }

  SimpleName findContext(List<SimpleName> classification) {
    SimpleName best = null;
    // Iterate in reverse order, start from second last
    ListIterator<SimpleName> li = classification.listIterator(classification.size()-1);
    while(li.hasPrevious()) {
      SimpleName mono = li.previous();
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
