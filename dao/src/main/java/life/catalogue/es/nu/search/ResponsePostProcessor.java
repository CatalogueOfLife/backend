package life.catalogue.es.nu.search;

import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.es.UpwardConverter;

/**
 * The last "converter" that the data pass through on the way upward before becoming a full-blown API object. Doesn't create a new type of
 * object but enhances it.
 *
 */
class ResponsePostProcessor implements UpwardConverter<NameUsageSearchResponse, NameUsageSearchResponse> {

  private NameUsageSearchRequest request;
  private NameUsageSearchResponse response;

  ResponsePostProcessor(NameUsageSearchRequest request, NameUsageSearchResponse response) {
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

  /*
   * If the user specified a constraint on the catalog key, then remove all decisions within a NameUsageWrapper whose dataset key does not
   * correspond to the specified catalog key.
   */
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
