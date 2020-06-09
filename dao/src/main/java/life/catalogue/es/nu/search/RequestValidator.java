package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.InvalidQueryException;
import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;

class RequestValidator {

  private final NameUsageSearchRequest request;

  RequestValidator(NameUsageSearchRequest request) {
    this.request = request;
  }

  void validateRequest() {

    if (request.hasFilter(USAGE_ID)) {
      if (!request.hasFilter(DATASET_KEY)) {
        throw invalidSearchRequest("When specifying a usage ID, the dataset key must also be specified");
      } else if (request.getFilters().size() != 2) {
        throw invalidSearchRequest("No filters besides dataset key allowed when specifying usage ID");
      }
    }

    // More validations ...
  }

  private static InvalidQueryException invalidSearchRequest(String msg, Object... msgArgs) {
    msg = "Invalid search request. " + String.format(msg, msgArgs);
    return new InvalidQueryException(msg);
  }

}
