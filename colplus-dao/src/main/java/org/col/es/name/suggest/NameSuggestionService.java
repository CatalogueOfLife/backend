package org.col.es.name.suggest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.col.api.model.Synonym;
import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.api.search.NameSuggestion;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.NameUsageMultiResponse;
import org.col.es.name.NameUsageResponse;
import org.col.es.name.NameUsageService;
import org.col.es.name.search.NameUsageSearchService;
import org.col.es.response.SearchHit;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.sort;

public class NameSuggestionService extends NameUsageService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  public NameSuggestionService(String indexName, RestClient client) {
    super(indexName, client);
  }

  public NameSuggestResponse suggestNames(NameSuggestRequest request) throws IOException {
    RequestTranslator translator = new RequestTranslator(request);
    EsSearchRequest snQuery = translator.getScientificNameQuery();
    List<NameSuggestion> suggestions = new ArrayList<>();
    if (request.isVernaculars()) {
      EsSearchRequest vnQuery = translator.getVernacularNameQuery();
      NameUsageMultiResponse multiResponse = executeMultiSearchRequest(index, snQuery, vnQuery);
      NameUsageResponse snResponse = multiResponse.getResponses().get(0);
      NameUsageResponse vnResponse = multiResponse.getResponses().get(1);
      snResponse.getHits().getHits().forEach(hit -> {
        suggestions.add(getSuggestion(hit, NameSuggestion.Type.SCIENTIFIC));
      });
      vnResponse.getHits().getHits().forEach(hit -> {
        suggestions.add(getSuggestion(hit, NameSuggestion.Type.VERNACULAR));
      });
      sort(suggestions, (s1, s2) -> (int) Math.signum(s1.getScore() - s2.getScore()));
    } else {
      NameUsageResponse snResponse = executeSearchRequest(index, snQuery);
      snResponse.getHits().getHits().forEach(hit -> {
        suggestions.add(getSuggestion(hit, NameSuggestion.Type.SCIENTIFIC));
      });
    }
    return new NameSuggestResponse(suggestions);
  }

  private static NameSuggestion getSuggestion(SearchHit<NameUsageDocument> hit, NameSuggestion.Type type) {
    NameSuggestion ns = new NameSuggestion();
    ns.setType(type);
    ns.setScore(hit.getScore());
    NameUsageDocument doc = hit.getSource();
    if (doc.getStatus().isSynonym()) {
      ns.setAcceptedName(doc.getAcceptedName());
    } else {
      ns.setAcceptedName(doc.getScientificName());
    }
    ns.setNomCode(doc.getNomCode());
    ns.setRank(doc.getRank());
    ns.setStatus(doc.getStatus());
    return ns;
  }

}
