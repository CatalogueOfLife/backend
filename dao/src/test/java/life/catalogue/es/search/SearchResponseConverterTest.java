package life.catalogue.es.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.FacetValue;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;

import static org.junit.Assert.*;

/**
 * Facets come back in one of two shapes: inside the global context (global -> filter -> facet filter -> terms) when a
 * facet has to ignore its own filter, or as plain top level terms aggregations when the counts are those of the query.
 */
public class SearchResponseConverterTest {

  private static Aggregate rankTerms() {
    return Aggregate.of(a -> a.lterms(lt -> lt
      .buckets(b -> b.array(List.of(
        LongTermsBucket.of(x -> x.key(Rank.SPECIES.ordinal()).docCount(9)),
        LongTermsBucket.of(x -> x.key(Rank.GENUS.ordinal()).docCount(1))
      )))
    ));
  }

  private static SearchResponse<NameUsageWrapper> response(Map<String, Aggregate> aggregations) {
    return SearchResponse.of(r -> r
      .took(1)
      .timedOut(false)
      .shards(s -> s.total(1).successful(1).failed(0))
      .hits(h -> h.total(t -> t.value(10).relation(TotalHitsRelation.Eq)).hits(List.of()))
      .aggregations(aggregations)
    );
  }

  private static void assertRankFacet(NameUsageSearchResponse resp) {
    Set<FacetValue<?>> rank = resp.getFacets().get(NameUsageSearchParameter.RANK);
    assertNotNull("rank facet missing", rank);
    assertEquals(2, rank.size());
    for (FacetValue<?> v : rank) {
      if (v.getValue() == Rank.SPECIES) {
        assertEquals(9, v.getCount());
      } else {
        assertEquals(Rank.GENUS, v.getValue());
        assertEquals(1, v.getCount());
      }
    }
  }

  @Test
  public void readsTopLevelFacets() throws Exception {
    var esResponse = response(Map.of(NameUsageSearchParameter.RANK.name(), rankTerms()));

    NameUsageSearchResponse resp = new SearchResponseConverter(esResponse, new NameUsageSearchRequest()).convertEsResponse(new Page());

    assertEquals(10, resp.getTotal());
    assertRankFacet(resp);
  }

  @Test
  public void readsGlobalContextFacets() throws Exception {
    Aggregate global = Aggregate.of(a -> a.global(g -> g
      .docCount(100)
      .aggregations(FacetsTranslator.FILTER_AGG_LABEL, Aggregate.of(f -> f.filter(ff -> ff
        .docCount(10)
        .aggregations(NameUsageSearchParameter.RANK.name(), Aggregate.of(r -> r.filter(rf -> rf
          .docCount(10)
          .aggregations(FacetsTranslator.FACET_AGG_LABEL, rankTerms())
        )))
      )))
    ));
    var esResponse = response(Map.of(FacetsTranslator.GLOBAL_AGG_LABEL, global));

    NameUsageSearchResponse resp = new SearchResponseConverter(esResponse, new NameUsageSearchRequest()).convertEsResponse(new Page());

    assertRankFacet(resp);
  }

  @Test
  public void facetOffsetSkipsLeadingBuckets() throws Exception {
    var esResponse = response(Map.of(NameUsageSearchParameter.RANK.name(), rankTerms()));
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setFacetOffset(1);

    NameUsageSearchResponse resp = new SearchResponseConverter(esResponse, req).convertEsResponse(new Page());

    Set<FacetValue<?>> rank = resp.getFacets().get(NameUsageSearchParameter.RANK);
    assertEquals(1, rank.size());
    assertEquals(Rank.GENUS, rank.iterator().next().getValue());
  }

  @Test
  public void noAggregationsNoFacets() throws Exception {
    var esResponse = response(Map.of());

    NameUsageSearchResponse resp = new SearchResponseConverter(esResponse, new NameUsageSearchRequest()).convertEsResponse(new Page());

    assertTrue(resp.getFacets().isEmpty());
  }
}
