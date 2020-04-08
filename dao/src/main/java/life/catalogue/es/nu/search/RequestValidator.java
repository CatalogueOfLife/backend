package life.catalogue.es.nu.search;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;
import life.catalogue.api.search.NameUsageSearchRequest;

class RequestValidator {

  private final NameUsageSearchRequest request;

  RequestValidator(NameUsageSearchRequest request) {
    this.request = request;
  }

  void validateRequest() {
    NameUsageSearchRequest copy = request.copy();
    if (copy.hasFilter(USAGE_ID)) {
      if (!copy.hasFilter(DATASET_KEY)) {
        throw invalidSearchRequest("When specifying a usage ID, the dataset key must also be specified");
      }
      copy.removeFilter(DATASET_KEY);
      copy.removeFilter(USAGE_ID);
      if (!copy.getFilters().isEmpty()) {
        throw invalidSearchRequest("No filters besides dataset key allowed when specifying usage ID");
      }
    }
    // TODO: More validations ...
  }

  private static IllegalArgumentException invalidSearchRequest(String msg, Object... msgArgs) {
    msg = "Bad search request. " + String.format(msg, msgArgs);
    return new IllegalArgumentException(msg);
  }

}
