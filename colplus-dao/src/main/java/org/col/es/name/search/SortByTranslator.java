package org.col.es.name.search;

import java.util.List;

import org.col.api.search.NameUsageSearchRequest;
import org.col.api.search.NameUsageSearchRequest.SortBy;
import org.col.es.query.CollapsibleList;
import org.col.es.query.SortField;

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
    return CollapsibleList.of(SortField.DOC);
  }

}
