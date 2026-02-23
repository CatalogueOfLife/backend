package life.catalogue.es2.query;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.api.search.NameUsageRequest.IS_NOT_NULL;
import static life.catalogue.api.search.NameUsageRequest.IS_NULL;
import static org.junit.Assert.*;

public class FilterTranslatorTest {

  @Test
  public void testFilterByStringParam() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.USAGE_ID, "abc123");
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.USAGE_ID);
    assertNotNull(q);
    assertTrue(q.isTerm());
    assertEquals("id", q.term().field());
    assertEquals("abc123", q.term().value().stringValue());
  }

  @Test
  public void testFilterByIntegerParam() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DATASET_KEY, 1000);
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.DATASET_KEY);
    assertNotNull(q);
    assertTrue(q.isTerm());
    assertEquals("usage.name.datasetKey", q.term().field());
    assertEquals(1000L, q.term().value().longValue());
  }

  @Test
  public void testFilterByMultipleValues() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.DATASET_KEY, 10);
    request.addFilter(NameUsageSearchParameter.DATASET_KEY, 20);
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.DATASET_KEY);
    assertNotNull(q);
    assertTrue(q.isTerms());
    assertEquals("usage.name.datasetKey", q.terms().field());
    assertEquals(2, q.terms().terms().value().size());
  }

  @Test
  public void testFilterByTaxonId() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.TAXON_ID, "taxon42");
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.TAXON_ID);
    assertNotNull(q);
    assertTrue(q.isTerm());
    assertEquals("classification.id", q.term().field());
    assertEquals("taxon42", q.term().value().stringValue());
  }

  @Test
  public void testFilterIsNull() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.SECTOR_KEY, IS_NULL);
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.SECTOR_KEY);
    assertNotNull(q);
    // IS_NULL generates a must_not exists query
    assertTrue(q.isBool());
    assertFalse(q.bool().mustNot().isEmpty());
  }

  @Test
  public void testFilterIsNotNull() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.SECTOR_KEY, IS_NOT_NULL);
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.SECTOR_KEY);
    assertNotNull(q);
    // IS_NOT_NULL generates an exists query
    assertTrue(q.isExists());
    assertEquals("sectorKey", q.exists().field());
  }

  @Test
  public void testFilterByRankUsesOrdinal() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);
    FilterTranslator ft = new FilterTranslator(request);
    Query q = ft.translate(NameUsageSearchParameter.RANK);
    assertNotNull(q);
    assertTrue(q.isTerm());
    assertEquals("usage.name.rank", q.term().field());
    // Rank is stored as integer (ordinal)
    assertEquals((long) Rank.SPECIES.ordinal(), q.term().value().longValue());
  }

  @Test
  public void testMinMaxRankFilter() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setMinRank(Rank.FAMILY);
    request.setMaxRank(Rank.SPECIES);
    var queries = FiltersTranslator.processMinMaxRank(request);
    assertFalse(queries.isEmpty());
    Query q = queries.get(0);
    assertTrue(q.isRange());
  }

  @Test
  public void testMinRankOnlyFilter() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setMinRank(Rank.FAMILY);
    var queries = FiltersTranslator.processMinMaxRank(request);
    assertEquals(1, queries.size());
    assertTrue(queries.get(0).isRange());
  }

  @Test
  public void testMaxRankOnlyFilter() {
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setMaxRank(Rank.GENUS);
    var queries = FiltersTranslator.processMinMaxRank(request);
    assertEquals(1, queries.size());
    assertTrue(queries.get(0).isRange());
  }

}
