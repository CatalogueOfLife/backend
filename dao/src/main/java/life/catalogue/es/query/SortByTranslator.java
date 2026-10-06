package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageRequest;

import org.gbif.nameparser.util.UnicodeUtils;

import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptSortType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.json.JsonData;

import static life.catalogue.api.search.NameUsageRequest.SearchContent.VERNACULAR_NAME;
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
      } else if (!FiltersTranslator.mustGenerateFilters(request)) {
        // No query and no filters => match_all. A field sort would force Elasticsearch to scan the entire index to
        // fill the result page (~1.3s over 150M+ usages on dev) just to order a result whose first-page order is
        // meaningless. Fall back to _doc (index order) so the first page returns almost instantly without a full scan.
        // Callers that genuinely want a specific order over the whole corpus can still request it explicitly via
        // sortBy, paying the scan cost (e.g. an alphabetical browse, or Issue #1513 accepted-before-synonym).
        return List.of(SortOptions.of(s -> s.field(f -> f.field("_doc"))));
      } else {
        // filtered but query-less: taxonomic order is intuitive and cheap, since the filters prune the set
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
          boolean vernacular = request.getContent() != null && request.getContent().contains(VERNACULAR_NAME);
          sorts.add(exactMatchSort(request.getQ(), vernacular));
        }
        sorts.add(statusOrderSort());
        sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        return sorts;
    }
  }

  /**
   * Primary sort tier for relevance searches that lifts documents whose scientific name or label
   * equals the query string to the very top, regardless of their taxonomic status and rank.
   * If vernacular names are searched, a vernacular name equal to the query also counts as exact.
   * Returns 0 for an exact match and 1 otherwise, sorted ascending. Relies on the {@code keyword}
   * doc values of {@code usage.name.scientificName}, {@code usage.label} and {@code vernacularNames.name.exact}.
   */
  static SortOptions exactMatchSort(String q, boolean vernacular) {
    // compare case insensitively: params.q is lower cased here, the doc values are lower cased in the script
    String src = "(doc['usage.name.scientificName'].size() > 0 && doc['usage.name.scientificName'].value.toLowerCase() == params.q) || "
      + "(doc['usage.label'].size() > 0 && doc['usage.label'].value.toLowerCase() == params.q)";
    if (vernacular) {
      // the vernacular doc values are already normalized by the index, see vernacular_exact normalizer in schema.json.
      // The containsKey guard keeps the script working on older indices without the exact sub-field.
      src += " || (doc.containsKey('" + QTranslator.FLD_VERNACULAR_EXACT + "') && doc['" + QTranslator.FLD_VERNACULAR_EXACT + "'].contains(params.vq))";
    }
    final String source = src + " ? 0 : 1";
    Script script = Script.of(s -> {
      s.source(ss -> ss.scriptString(source))
       .params("q", JsonData.of(q.toLowerCase(java.util.Locale.ROOT)));
      if (vernacular) {
        s.params("vq", JsonData.of(UnicodeUtils.foldToAscii(q).trim().toLowerCase(java.util.Locale.ROOT)));
      }
      return s;
    });
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
