package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.CollapsibleList;
import life.catalogue.es.query.SortField;

import java.util.List;

import static life.catalogue.api.search.NameUsageSearchRequest.SortBy.TAXONOMIC;

class SortByTranslator {

  private final NameUsageSearchRequest request;

  SortByTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  List<SortField> translate() {
    if (request.getSortBy() == null) {
      request.setSortBy(TAXONOMIC);
    }

    switch (request.getSortBy()) {
      case INDEX_NAME_ID:
        return CollapsibleList.of(new SortField("nameIndexIds", !request.isReverse()));
      case NAME:
        return CollapsibleList.of(new SortField("scientificName", !request.isReverse()));
      case NATIVE:
        return CollapsibleList.of(SortField.DOC);
      case RELEVANCE:
        return CollapsibleList.of(SortField.SCORE);
      case TAXONOMIC:
      default:
        return CollapsibleList.of(new SortField("rank", !request.isReverse()), new SortField("scientificName"));
    }
  }

}
