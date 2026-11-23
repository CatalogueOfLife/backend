package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.InvalidQueryException;

import java.util.Set;

import static life.catalogue.api.search.NameUsageRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageRequest.IS_NULL;
import static life.catalogue.api.search.NameUsageSearchParameter.*;

public class SearchRequestValidator {
  private static final int MAX_SIZE_AGGS = 100000;

  private final NameUsageSearchRequest request;

  public SearchRequestValidator(NameUsageSearchRequest request) {
    this.request = request;
  }

  public void validateRequest() {
    if (NameUsageRequest.SearchType.EXACT == request.getSearchType()){
      request.setContent(Set.of(NameUsageRequest.SearchContent.SCIENTIFIC_NAME));
    } else if (request.getContent() == null || request.getContent().isEmpty()) {
       request.setContentDefault();
    }
    if (request.hasFilter(DECISION_MODE)) {
      // require a project key
      if (!request.hasFilter(PROJECT_KEY) || request.getFilterValues(PROJECT_KEY).size() > 1) {
        throw invalidSearchRequest("When specifying a decision mode, a single catalogue key must also be specified");
      }
    }
    // the publisher is not indexed but resolved to its datasets, so it can neither be counted nor be absent
    if (request.getFacets().contains(PUBLISHER_KEY)) {
      throw invalidSearchRequest("No facet on %s, use %s instead", PUBLISHER_KEY, DATASET_KEY);
    }
    if (request.hasFilter(PUBLISHER_KEY)) {
      for (Object v : request.getFilterValues(PUBLISHER_KEY)) {
        if (v.equals(IS_NULL) || v.equals(IS_NOT_NULL)) {
          throw invalidSearchRequest("The %s filter takes GBIF publisher keys only", PUBLISHER_KEY);
        }
      }
    }
    // we set a maximum limit for performance reasons
    if (request.getFacetLimit() != null && request.getFacetLimit() > MAX_SIZE_AGGS) {
      throw new IllegalArgumentException("Facets paging is only supported up to " + MAX_SIZE_AGGS + " elements");
    }
  }

  private static InvalidQueryException invalidSearchRequest(String msg, Object... msgArgs) {
    msg = "Invalid search request. " + String.format(msg, msgArgs);
    return new InvalidQueryException(msg);
  }

}
