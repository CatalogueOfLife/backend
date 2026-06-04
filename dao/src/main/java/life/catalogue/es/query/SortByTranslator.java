package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageRequest;

import java.util.List;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;

import static life.catalogue.api.search.NameUsageRequest.SortBy.RELEVANCE;
import static life.catalogue.api.search.NameUsageRequest.SortBy.TAXONOMIC;

public class SortByTranslator {

  private final NameUsageRequest request;

  public SortByTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public List<SortOptions> translate() {
    if (request.getSortBy() == null) {
      request.setSortBy(RELEVANCE);
    }

    SortOrder order = request.isReverse() ? SortOrder.Desc : SortOrder.Asc;

    switch (request.getSortBy()) {
      case NAME:
        return List.of(SortOptions.of(s -> s.field(f -> f.field("usage.name.scientificName").order(order))));
      case TAXONOMIC:
        // accepted taxa first (regardless of rank), then by rank and name within each status group
        return List.of(
          statusOrderSort(),
          SortOptions.of(s -> s.field(f -> f.field("usage.name.rank").order(order))),
          SortOptions.of(s -> s.field(f -> f.field("usage.name.scientificName").order(SortOrder.Asc)))
        );
      case RELEVANCE:
      default:
        // accepted taxa first (regardless of rank), then by relevance score within each status group
        return List.of(
          statusOrderSort(),
          SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)))
        );
    }
  }

  /**
   * Primary sort tier that groups accepted taxa (statusOrder 0) above synonyms (1) and bare names (2),
   * regardless of their rank. See {@code NameUsageWrapper.getStatusOrder()}.
   */
  private static SortOptions statusOrderSort() {
    return SortOptions.of(s -> s.field(f -> f.field("usage.statusOrder").order(SortOrder.Asc)));
  }

}
