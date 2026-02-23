package life.catalogue.es2.search;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.Issue;

import java.util.Map;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static org.junit.Assert.*;

public class FacetsTranslatorTest {

  @Test
  public void testTranslateReturnsSingleGlobalAgg() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(STATUS);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    assertEquals(1, aggs.size());
    Aggregation globalAgg = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL);
    assertNotNull(globalAgg);
    assertTrue(globalAgg.isGlobal());
  }

  @Test
  public void testTranslateContainsFilterAgg() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(DATASET_KEY);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    Aggregation filterAgg = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL)
        .aggregations().get(FacetsTranslator.FILTER_AGG_LABEL);
    assertNotNull(filterAgg);
    assertTrue(filterAgg.isFilter());
  }

  @Test
  public void testTranslateContainsPerFacetAggs() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(STATUS);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    Map<String, Aggregation> facetAggs = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL)
        .aggregations().get(FacetsTranslator.FILTER_AGG_LABEL)
        .aggregations();

    assertTrue(facetAggs.containsKey(STATUS.name()));
    assertTrue(facetAggs.containsKey(DATASET_KEY.name()));
    assertTrue(facetAggs.containsKey(RANK.name()));
  }

  @Test
  public void testEachFacetAggHasTermsSubAgg() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(STATUS);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    Aggregation facetFilterAgg = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL)
        .aggregations().get(FacetsTranslator.FILTER_AGG_LABEL)
        .aggregations().get(STATUS.name());
    assertNotNull(facetFilterAgg);
    assertTrue(facetFilterAgg.isFilter());

    Aggregation termsAgg = facetFilterAgg.aggregations().get(FacetsTranslator.FACET_AGG_LABEL);
    assertNotNull(termsAgg);
    assertTrue(termsAgg.isTerms());
  }

  @Test
  public void testFacetWithFiltersAndQ() {
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
  public void testFacetLimitApplied() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFacet(RANK);
    request.setFacetLimit(5);

    Map<String, Aggregation> aggs = new FacetsTranslator(request).translate();

    Aggregation termsAgg = aggs.get(FacetsTranslator.GLOBAL_AGG_LABEL)
        .aggregations().get(FacetsTranslator.FILTER_AGG_LABEL)
        .aggregations().get(RANK.name())
        .aggregations().get(FacetsTranslator.FACET_AGG_LABEL);

    assertEquals(5, (int) termsAgg.terms().size());
  }

}
