package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SortBy;
import life.catalogue.es.query.CollapsibleList;
import life.catalogue.es.query.SortField;

import java.util.List;

class SortByTranslator {

  private final NameUsageSearchRequest request;

  SortByTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  List<SortField> translate() {
    if (request.getSortBy() == SortBy.NAME) {
      return CollapsibleList.of(new SortField("scientificName", !request.isReverse()));
    } else if (request.getSortBy() == SortBy.INDEX_NAME_ID) {
      return CollapsibleList.of(new SortField("nameIndexId", !request.isReverse()));
    }
    return CollapsibleList.of(new SortField("rank", !request.isReverse()), new SortField("scientificName"));
  }

}
