package life.catalogue.es2.query;

import life.catalogue.api.search.NameUsageRequest;

import java.util.List;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;

import static life.catalogue.api.search.NameUsageRequest.SortBy.TAXONOMIC;

public class SortByTranslator {

  private final NameUsageRequest request;

  public SortByTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public List<SortOptions> translate() {
    if (request.getSortBy() == null) {
      request.setSortBy(TAXONOMIC);
    }

    SortOrder order = request.isReverse() ? SortOrder.Desc : SortOrder.Asc;

    switch (request.getSortBy()) {
      case NAME:
        return List.of(SortOptions.of(s -> s.field(f -> f.field("usage.name.scientificName").order(order))));
      case NATIVE:
        return List.of(SortOptions.of(s -> s.doc(d -> d)));
      case RELEVANCE:
        return List.of(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
      case TAXONOMIC:
      default:
        return List.of(
          SortOptions.of(s -> s.field(f -> f.field("usage.name.rank").order(order))),
          SortOptions.of(s -> s.field(f -> f.field("usage.name.scientificName").order(SortOrder.Asc)))
        );
    }
  }

}
