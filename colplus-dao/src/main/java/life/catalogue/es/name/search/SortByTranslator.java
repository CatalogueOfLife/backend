package life.catalogue.es.name.search;

import java.util.List;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SortBy;
import life.catalogue.es.query.CollapsibleList;
import life.catalogue.es.query.SortField;

class SortByTranslator {

  private final NameUsageSearchRequest request;

  SortByTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  List<SortField> translate() {
    if (request.getSortBy() == SortBy.NAME) {
      return CollapsibleList.of(new SortField("scientificName", !request.isReverse()));
    }
    if (request.getSortBy() == SortBy.TAXONOMIC) {
      return CollapsibleList.of(new SortField("rank", !request.isReverse()), new SortField("scientificName"));
    }
    return CollapsibleList.of(request.isReverse() ? SortField.DOC_DESC : SortField.DOC);
  }

}
