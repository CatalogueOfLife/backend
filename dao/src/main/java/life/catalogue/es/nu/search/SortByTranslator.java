package life.catalogue.es.nu.search;

import java.util.List;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.CollapsibleList;
import life.catalogue.es.query.SortField;

class SortByTranslator {

  private final NameUsageSearchRequest request;

  SortByTranslator(NameUsageSearchRequest request) {
    this.request = request;
  }

  List<SortField> translate() {
    if (request.getSortBy() == null) {
      return CollapsibleList.of(new SortField("rank", !request.isReverse()), new SortField("scientificName"));
    }
    switch (request.getSortBy()) {
      case INDEX_NAME_ID:
        return CollapsibleList.of(new SortField("nameIndexId", !request.isReverse()));
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
