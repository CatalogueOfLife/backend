package life.catalogue.es.suggest;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.query.InvalidQueryException;

import org.apache.commons.lang3.StringUtils;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static life.catalogue.api.vocab.TaxonomicStatus.ACCEPTED;
import static life.catalogue.api.vocab.TaxonomicStatus.PROVISIONALLY_ACCEPTED;

public class SuggestRequestValidator {

  private final NameUsageSuggestRequest request;

  public SuggestRequestValidator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  public void validateRequest() {
    if (request.hasFilter(DECISION_MODE)) {
      // require a project key
      if (!request.hasFilter(CATALOGUE_KEY) || request.getFilterValues(CATALOGUE_KEY).size() > 1) {
        throw invalidRequest("When specifying a decision mode, a single catalogue key must also be specified");
      }
    }
    if (request.getLimit() == null) {
      request.setLimit(10);
    } else if (request.getLimit() < 0 || request.getLimit() > 100) {
      throw invalidRequest("A maximum of 100 suggestions is allowed per request");
    }
    if (StringUtils.isBlank(request.getQ())) {
      throw invalidRequest("Missing q parameter");
    }
    if (!request.hasFilter(DATASET_KEY) || request.getFilters().get(DATASET_KEY).size() > 1) {
      throw invalidRequest("A single dataset key must be specified");
    }
    // defaults
    if (request.isAccepted()) {
      request.clearFilter(NameUsageSearchParameter.STATUS);
      request.addFilter(NameUsageSearchParameter.STATUS, ACCEPTED);
      request.addFilter(NameUsageSearchParameter.STATUS, PROVISIONALLY_ACCEPTED);
    } else if (request.isExclBareNames() && !request.hasFilter(NameUsageSearchParameter.STATUS)) {
      for (var status : TaxonomicStatus.values()) {
        if (!status.isBareName()) {
          request.addFilter(NameUsageSearchParameter.STATUS, status);
        }
      }
    }
  }

  private static InvalidQueryException invalidRequest(String msg, Object... msgArgs) {
    msg = "Invalid suggest request. " + String.format(msg, msgArgs);
    return new InvalidQueryException(msg);
  }

}
