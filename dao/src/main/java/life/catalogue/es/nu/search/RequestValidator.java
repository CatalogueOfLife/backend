package life.catalogue.es.nu.search;

import java.util.Set;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.InvalidQueryException;
import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_MODE;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageSearchRequest.IS_NULL;

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

    // Decision-related validations
    if (request.hasFilter(CATALOGUE_KEY)) {
      Set<Object> values = request.getFilterValues(CATALOGUE_KEY);
      if (values.contains(IS_NULL)) {
        if (values.size() != 1) {
          throw invalidSearchRequest("_IS_NULL query on catalogueKey must be the only query on that field");
        } else if (request.hasFilter(DECISION_MODE)) {
          throw invalidSearchRequest("_IS_NULL query on catalogueKey cannot be combined with query on decisionMode");
        }
      } else if (values.contains(IS_NOT_NULL)) {
        if (values.size() != 1) {
          throw invalidSearchRequest("_IS_NOT_NULL query on catalogueKey must be the only query on that field");
        } else if (request.hasFilter(DECISION_MODE)) {
          throw invalidSearchRequest("_IS_NOT_NULL query on catalogueKey cannot be combined with query on decisionMode");
        }
      }
    }
    if (request.hasFilter(DECISION_MODE)) {
      Set<Object> values = request.getFilterValues(DECISION_MODE);
      if (values.contains(IS_NULL) && values.size() != 1) {
        throw new InvalidQueryException("_IS_NULL query on decisionMode must be the only query on that field");
      } else if (values.contains(IS_NOT_NULL) && values.size() != 1) {
        throw new InvalidQueryException("_IS_NOT_NULL query on decisionMode must be the only query on that field");
      }
    }

    // More validations ...
  }

  private static InvalidQueryException invalidSearchRequest(String msg, Object... msgArgs) {
    msg = "Invalid search request. " + String.format(msg, msgArgs);
    return new InvalidQueryException(msg);
  }

}
