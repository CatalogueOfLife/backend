package life.catalogue.es.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Map;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static org.junit.Assert.*;

/**
 * A facet is aggregated inside the query context unless the request filters on that very facet: only then do the counts
 * have to ignore the facet's own filter, which needs the global context. A global aggregation scans every document of
 * every shard, so the taxon page request - TAXON_ID and status filters, a rank facet - must never pay for it.
 */
public class FacetsTranslatorTest {

  @Test
  public void facetWithoutOwnFilterStaysInQueryContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.addFilter(TAXON_ID, "N");
    request.addFilter(STATUS, TaxonomicStatus.ACCEPTED, TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    request.addFilter(DATASET_KEY, 3);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertNull("no global context without an own filter", aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL));
    assertEquals(1, aggs.size());
    Aggregation rank = aggs.get(RANK.name());
    assertNotNull(rank);
    assertTrue(rank.isTerms());
    assertEquals("usage.name.rank", rank.terms().field());
  }

  @Test
  public void singleFacetWithoutAnyFilterStaysInQueryContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(STATUS);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertNull(aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL));
    assertTrue(aggs.get(STATUS.name()).isTerms());
  }

  @Test
  public void severalFacetsWithoutOwnFiltersStayInQueryContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(STATUS);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFilter(TAXON_ID, "N");
    request.setQ("Abies");

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertNull(aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL));
    assertEquals(3, aggs.size());
    assertTrue(aggs.get(STATUS.name()).isTerms());
    assertTrue(aggs.get(DATASET_KEY.name()).isTerms());
    assertTrue(aggs.get(RANK.name()).isTerms());
  }

  @Test
  public void facetLimitAndOffsetAppliedInQueryContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.setFacetLimit(5);
    request.setFacetOffset(2);
    request.setFacetMinCount(3);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    var terms = aggs.get(RANK.name()).terms();
    assertEquals(7, (int) terms.size());
    assertEquals(3, (int) terms.minDocCount());
  }

  @Test
  public void facetWithOwnFilterNeedsGlobalContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.addFilter(RANK, Rank.SPECIES);
    request.addFilter(DATASET_KEY, 3);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertEquals(1, aggs.size());
    Aggregation globalAgg = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL);
    assertNotNull(globalAgg);
    assertTrue(globalAgg.isGlobal());

    Aggregation filterAgg = globalAgg.aggregations().get(FacetsTranslator.FILTER_AGG_LABEL);
    assertNotNull(filterAgg);
    assertTrue(filterAgg.isFilter());

    Aggregation facetFilterAgg = filterAgg.aggregations().get(RANK.name());
    assertNotNull(facetFilterAgg);
    assertTrue(facetFilterAgg.isFilter());

    Aggregation termsAgg = facetFilterAgg.aggregations().get(FacetsTranslator.FACET_AGG_LABEL);
    assertNotNull(termsAgg);
    assertTrue(termsAgg.isTerms());
  }

  @Test
  public void oneFacetWithOwnFilterPutsAllFacetsInGlobalContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.addFacet(STATUS);
    request.addFacet(ISSUE);
    request.addFilter(STATUS, TaxonomicStatus.SYNONYM);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertEquals(1, aggs.size());
    Map<String, Aggregation> facetAggs = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL)
        .aggregations().get(FacetsTranslator.FILTER_AGG_LABEL)
        .aggregations();
    assertTrue(facetAggs.containsKey(RANK.name()));
    assertTrue(facetAggs.containsKey(STATUS.name()));
    assertTrue(facetAggs.containsKey(ISSUE.name()));
  }

  @Test
  public void facetIncludeSelfStaysInQueryContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.addFilter(RANK, Rank.SPECIES);
    request.setFacetIncludeSelf(true);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertNull(aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL));
    assertTrue(aggs.get(RANK.name()).isTerms());
  }

  @Test
  public void facetWithOwnFilterAndQNeedsGlobalContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    request.addFilter(DATASET_KEY, 10);
    request.setQ("Abies");

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertNotNull(aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL));
  }

  @Test
  public void facetLimitAppliedInGlobalContext() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.addFilter(RANK, Rank.GENUS);
    request.setFacetLimit(5);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    Aggregation termsAgg = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL)
        .aggregations().get(FacetsTranslator.FILTER_AGG_LABEL)
        .aggregations().get(RANK.name())
        .aggregations().get(FacetsTranslator.FACET_AGG_LABEL);

    assertEquals(5, (int) termsAgg.terms().size());
  }

}
