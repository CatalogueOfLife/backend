package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.es.query.CollapsibleList;
import life.catalogue.es.query.SortField;

import java.util.List;

import static life.catalogue.api.search.NameUsageSearchRequest.SortBy.TAXONOMIC;

public class SortByTranslator {

  private final NameUsageRequest request;

  public SortByTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public List<SortField> translate() {
    if (request.getSortBy() == null) {
      request.setSortBy(TAXONOMIC);
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
