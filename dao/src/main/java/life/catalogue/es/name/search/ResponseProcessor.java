package life.catalogue.es.name.search;

import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.SimpleDecision;

class ResponseProcessor {

  private final NameUsageSearchRequest request;
  private final NameUsageSearchResponse response;

  ResponseProcessor(NameUsageSearchRequest request, NameUsageSearchResponse response) {
    this.request = request;
    this.response = response;
  }

  NameUsageSearchResponse processResponse() {
    if (!response.getResult().isEmpty()) {
      if (mustPruneDecisions()) {
        pruneDecisions();
      }
      if (mustHighlight()) {
        NameSearchHighlighter highlighter = new NameSearchHighlighter(request, response);
        highlighter.highlightNameUsages();
      }
    }
    return response;
  }

  private void pruneDecisions() {
    response.getResult().stream().forEach(nuw -> {
      if (nuw.getDecisions() != null) {
        SimpleDecision match = nuw.getDecisions().stream()
            .filter(d -> request.getFilterValues(CATALOGUE_KEY).contains(d.getDatasetKey()))
            .findFirst().orElse(null);
        nuw.setDecisions(match == null ? null : Arrays.asList(match));
      }
    });
  }

  private boolean mustPruneDecisions() {
    // Both-query-and-prune:
    return request.hasFilter(CATALOGUE_KEY);
    // Either-query-or-prune:
    // return request.hasFilter(CATALOGUE_KEY) && !request.hasFilter(DECISION_MODE);
  }

  private boolean mustHighlight() {
    return request.isHighlight() && !StringUtils.isEmpty(request.getQ()) && !request.hasFilter(USAGE_ID);
  }

}
