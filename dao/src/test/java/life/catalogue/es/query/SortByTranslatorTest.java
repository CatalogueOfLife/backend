package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.SortOptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SortByTranslatorTest {

  private static boolean isDocSort(List<SortOptions> sorts) {
    return sorts.size() == 1 && sorts.get(0).isField() && "_doc".equals(sorts.get(0).field().field());
  }

  /**
   * A query-less, filter-less search is a match_all. With no explicit sort requested we fall back to _doc order to
   * avoid a full index scan, since the order of a match_all page is meaningless.
   */
  @Test
  public void matchAllWithoutExplicitSortUsesDoc() {
    var req = new NameUsageSearchRequest(); // no q, no filters, no sortBy (would otherwise default to TAXONOMIC)
    assertTrue(isDocSort(new SortByTranslator(req).translate()));
  }

  /**
   * An explicitly requested sort is honored even for a match_all (e.g. alphabetical browse, or Issue #1513
   * accepted-before-synonym), accepting the full-scan cost - we only optimize the default, unspecified case.
   */
  @Test
  public void matchAllWithExplicitSortIsHonored() {
    var req = new NameUsageSearchRequest();
    req.setSortBy(NameUsageRequest.SortBy.RELEVANCE);
    assertFalse(isDocSort(new SortByTranslator(req).translate()));
  }

  @Test
  public void querySortsByRelevanceNotDoc() {
    var req = new NameUsageSearchRequest();
    req.setQ("abies");
    assertFalse(isDocSort(new SortByTranslator(req).translate()));
  }

  private static String exactTierScript(NameUsageSearchRequest req) {
    var sorts = new SortByTranslator(req).translate();
    assertTrue(sorts.get(0).isScript());
    return sorts.get(0).script().script().source().scriptString();
  }

  /**
   * Exact vernacular name matches only join the exact match sort tier when vernacular names are searched.
   * See https://github.com/gbif/taxon-ws/issues/71
   */
  @Test
  public void exactTierIncludesVernacularOnlyIfSearched() {
    var req = new NameUsageSearchRequest();
    req.setQ("oak");
    req.setSingleContent(NameUsageRequest.SearchContent.SCIENTIFIC_NAME);
    assertFalse(exactTierScript(req).contains(QTranslator.FLD_VERNACULAR_EXACT));

    req = new NameUsageSearchRequest();
    req.setQ("oak");
    req.setSingleContent(NameUsageRequest.SearchContent.VERNACULAR_NAME);
    assertTrue(exactTierScript(req).contains(QTranslator.FLD_VERNACULAR_EXACT));

    var sort = SortByTranslator.exactMatchSort(" Chêne ", true);
    assertEquals("chene", sort.script().script().params().get("vq").to(String.class));
    assertEquals(" chêne ", sort.script().script().params().get("q").to(String.class));
  }

  @Test
  public void filteredSortsByConfiguredOrderNotDoc() {
    var req = new NameUsageSearchRequest();
    // a non PROJECT_KEY filter makes mustGenerateFilters true, so the query is no longer match_all
    req.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);
    req.setSortBy(NameUsageRequest.SortBy.TAXONOMIC);
    var sorts = new SortByTranslator(req).translate();
    assertFalse(isDocSort(sorts));
    assertEquals(3, sorts.size()); // taxonomic = statusOrder, rank, scientificName
  }
}
