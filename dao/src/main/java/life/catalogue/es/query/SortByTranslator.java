package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageRequest;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;

import static life.catalogue.api.search.NameUsageRequest.SortBy.RELEVANCE;
import static life.catalogue.api.search.NameUsageRequest.SortBy.TAXONOMIC;

public class SortByTranslator {

  private final NameUsageRequest request;

  public SortByTranslator(NameUsageRequest request) {
    this.request = request;
  }

  public List<SortOptions> translate() {
    if (request.getSortBy() == null) {
      if (request.hasQ()) {
        // default to relevance sorting for non-empty queries
        request.setSortBy(RELEVANCE);
      } else {
        // use taxonomic order otherwise, which is more intuitive for users
        request.setSortBy(TAXONOMIC);
      }
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
        // 1. exact name matches first, regardless of taxonomic status and rank
        // 2. accepted taxa before synonyms (also orders accepted vs synonym within the exact group)
        // 3. relevance score within each group
        List<SortOptions> sorts = new ArrayList<>(3);
        if (request.hasQ()) {
          sorts.add(exactMatchSort(request.getQ()));
        }
        sorts.add(statusOrderSort());
        sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        return sorts;
    }
  }

  /**
   * Primary sort tier for relevance searches that lifts documents whose scientific name or label
   * equals the query string to the very top, regardless of their taxonomic status and rank.
   * Returns 0 for an exact match and 1 otherwise, sorted ascending. Relies on the {@code keyword}
   * doc values of {@code usage.name.scientificName} and {@code usage.label}, so no schema change
   * or extra indexed field is needed.
   */
  private static SortOptions exactMatchSort(String q) {
    // compare case insensitively: params.q is lower cased here, the doc values are lower cased in the script
    Script script = Script.of(s -> s
      .source(src -> src.scriptString(
        "(doc['usage.name.scientificName'].size() > 0 && doc['usage.name.scientificName'].value.toLowerCase() == params.q) || "
          + "(doc['usage.label'].size() > 0 && doc['usage.label'].value.toLowerCase() == params.q) ? 0 : 1"))
      .params("q", JsonData.of(q.toLowerCase(java.util.Locale.ROOT)))
    );
    return SortOptions.of(s -> s.script(ss -> ss
      .type(ScriptSortType.Number)
      .script(script)
      .order(SortOrder.Asc)
    ));
  }

  /**
   * Primary sort tier that groups accepted taxa (statusOrder 0) above synonyms (1) and bare names (2),
   * regardless of their rank. See {@code NameUsageWrapper.getStatusOrder()}.
   */
  private static SortOptions statusOrderSort() {
    return SortOptions.of(s -> s.field(f -> f.field("usage.statusOrder").order(SortOrder.Asc)));
  }

}
