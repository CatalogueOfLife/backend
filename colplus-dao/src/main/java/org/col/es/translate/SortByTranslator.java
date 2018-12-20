package org.col.es.translate;

import java.util.List;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.es.query.CollapsibleList;
import org.col.es.query.SortField;

class SortByTranslator {

  private static final SortField SORTFIELD_NAME = new SortField("scientificNameWN");
  private static final SortField SORTFIELD_RANK = new SortField("rank");
  private static final SortField SORTFIELD_NATIVE = SortField.DOC;

  private final NameSearchRequest request;

  SortByTranslator(NameSearchRequest request) {
    this.request = request;
  }

  List<SortField> translate() {
    if (request.getSortBy() == SortBy.NAME) {
      return CollapsibleList.of(SORTFIELD_NAME);
    }
    if (request.getSortBy() == SortBy.TAXONOMIC) {
      return CollapsibleList.of(SORTFIELD_RANK, SORTFIELD_NAME);
    }
    return CollapsibleList.of(SORTFIELD_NATIVE);
  }

}
