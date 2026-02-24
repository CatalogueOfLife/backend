package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.InvalidQueryException;

import java.util.Set;

import static life.catalogue.api.search.NameUsageSearchParameter.CATALOGUE_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.DECISION_MODE;

public class SearchRequestValidator {

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
