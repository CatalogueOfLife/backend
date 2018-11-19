package org.col.es.translate;

import java.util.List;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.es.query.SortField;
import org.col.es.util.CollapsibleList;

class SortByTranslator {

  private final NameSearchRequest request;

  SortByTranslator(NameSearchRequest request) {
    this.request = request;
  }

  List<SortField> translate() {
    if (request.getSortBy() == SortBy.NAME) {
      return CollapsibleList.of(new SortField("scientificName"));
    }
    if (request.getSortBy() == SortBy.TAXONOMIC) {
      return CollapsibleList.of(new SortField("rank"), new SortField("scientificName"));
    }
    return CollapsibleList.of(new SortField("_doc"));
  }

}
