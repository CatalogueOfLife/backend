package life.catalogue.es.query;

import life.catalogue.api.search.NameUsageSearchRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static org.junit.Assert.*;

public class FiltersTranslatorTest {

  private static NameUsageSearchRequest publisherRequest(Set<Integer> datasetKeys) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(PUBLISHER_KEY, UUID.randomUUID());
    req.setPublisherDatasetKeys(datasetKeys);
    return req;
  }

  /**
   * The publisher is not indexed, it filters the dataset keys it was resolved to.
   */
  @Test
  public void publisher() {
    Query q = new FiltersTranslator(publisherRequest(Set.of(30, 1000, 7))).translate();
    assertTrue(q.isTerms());
    assertEquals("usage.name.datasetKey", q.terms().field());
    assertEquals(List.of("7", "30", "1000"),
      q.terms().terms().value().stream().map(FieldValue::stringValue).toList()
    );
  }

  @Test
  public void publisherWithoutDatasets() {
    Query q = new FiltersTranslator(publisherRequest(Set.of())).translate();
    assertTrue(q.isMatchNone());
  }

  @Test
  public void publisherAndDataset() {
    NameUsageSearchRequest req = publisherRequest(Set.of(30, 1000));
    req.addFilter(DATASET_KEY, 1000);
    Query q = new FiltersTranslator(req).translate();
    // both filter the same field, ANDed and not ORed
    assertTrue(q.isBool());
    assertEquals(2, q.bool().filter().size());
  }

  @Test(expected = IllegalStateException.class)
  public void publisherUnresolved() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(PUBLISHER_KEY, UUID.randomUUID());
    new FiltersTranslator(req).translate();
  }

  @Test
  public void copyKeepsResolvedPublisher() {
    NameUsageSearchRequest req = publisherRequest(Set.of(30));
    assertEquals(Set.of(30), req.copy().getPublisherDatasetKeys());
  }
}
