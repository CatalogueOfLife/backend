package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.InvalidQueryException;

import static life.catalogue.api.search.NameUsageSearchParameter.*;

class RequestValidator {

  private final NameUsageSearchRequest request;

  RequestValidator(NameUsageSearchRequest request) {
    this.request = request;
  }

  void validateRequest() {
    if (request.getContent() == null || request.getContent().isEmpty()) {
       request.setContentDefault();
    }
    if (request.hasFilter(USAGE_ID)) {
      if (!request.hasFilter(DATASET_KEY)) {
        throw invalidSearchRequest("When specifying a usage ID, the dataset key must also be specified");
      } else if (request.getFilters().size() != 2) {
        throw invalidSearchRequest("No filters besides dataset key allowed when specifying usage ID");
      }
    }
    if (request.hasFilter(DECISION_MODE)) {
      // require a project key
      if (!request.hasFilter(CATALOGUE_KEY) || request.getFilterValues(CATALOGUE_KEY).size() > 1) {
        throw invalidSearchRequest("When specifying a decision mode, a single catalogue key must also be specified");
      }
    }
  }

  private static InvalidQueryException invalidSearchRequest(String msg, Object... msgArgs) {
    msg = "Invalid search request. " + String.format(msg, msgArgs);
    return new InvalidQueryException(msg);
  }

}
