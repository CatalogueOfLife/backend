package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.SimpleNameCached;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Shared contract of all usage stores. Usages are always added through a {@link UsageSink} and read from
 * the {@link UsageMatcherStore} it seals into - for the mutable stores the two are the same instance, for
 * the file store the sink is a builder producing a sealed store.
 */
public abstract class UsageMatcherStoreTestBase {

  static SimpleNameCached add(SimpleNameCached snc, UsageSink sink) {
    sink.add(snc);
    return new SimpleNameCached(snc);
  }

  @Test
  public void empty() throws IOException {
    try (UsageMatcherStore store = seal(createSink(1))) {
      assertEquals(0, store.size());
      assertEquals(0, store.canonicalSize());
      assertTrue(store.isEmpty());
      assertNotFound("nope", store);
      assertTrue(store.simpleNamesByCanonicalId(13).isEmpty());
    }
  }

  @Test
  public void basics() throws IOException {
    var sink = createSink(1);
    var sn0 = add(snc("0", null, "Ausaceae", null, Rank.FAMILY, 0, 10), sink);
    var sn1 = add(snc("1", "4", "Aus bus", "Smith", Rank.SPECIES, 1, 11), sink);
    var sn2 = add(snc("2", "4", "Aus cus", "Miller", Rank.SPECIES, 2, 12), sink);
    var sn3 = add(snc("3", "1", "Aus bus cus", "(Miller)", Rank.SUBSPECIES, 3, 13), sink);
    var sn4 = add(snc("4", "0", "Aus", "Green", Rank.GENUS, 4, 14), sink);
    var sn5 = add(sncSyn("5", "2", "Aus cus", "Jackson", Rank.SPECIES, 2, 15), sink);

    try (UsageMatcherStore store = seal(sink)) {
      assertEquals(6, store.size());
      // 6 usages but only 5 distinct canonical ids (sn2 and sn5 share canonical id 2)
      assertEquals(5, store.canonicalSize());

      assertEquals(sn2, store.get(sn2.getId()));
      var cl = store.getClassification(sn5.getParentId());
      assertEquals(3, cl.size());
      assertEquals(sn5.getParentId(), cl.get(0).getId());

      assertEquals(sn2, store.get(sn2.getId()));
      cl = store.getClassification(sn5.getParentId());
      assertEquals(3, cl.size());
      assertEquals(sn5.getParentId(), cl.get(0).getId());

      assertEquals(sn4, store.get(sn4.getId()));
      assertNotFound("44", store);

      // every usage is reachable through all()
      var allIds = stream(store.all()).map(SimpleNameCached::getId).collect(Collectors.toSet());
      assertEquals(java.util.Set.of("0", "1", "2", "3", "4", "5"), allIds);
      assertEquals(java.util.Set.of(0, 1, 2, 3, 4), stream(store.allCanonicalIds()).collect(Collectors.toSet()));

      // the shared canonical returns both usages
      var shared = store.simpleNamesByCanonicalId(2).stream().map(SimpleNameCached::getId).collect(Collectors.toSet());
      assertEquals(java.util.Set.of("2", "5"), shared);
      assertEquals(java.util.List.of("0"), store.simpleNamesByCanonicalId(0).stream().map(SimpleNameCached::getId).toList());
      assertTrue(store.simpleNamesByCanonicalId(99).isEmpty());
      assertEquals(sn0.getName(), store.get("0").getName());
      assertEquals(sn1.getAuthorship(), store.get("1").getAuthorship());
      assertEquals(sn3.getRank(), store.get("3").getRank());

      // candidates come with a classification, resolved lazily
      var candidates = store.usagesByCanonicalId(3);
      assertEquals(1, candidates.size());
      // sn3 -> sn1 -> sn4 -> sn0
      assertEquals(3, candidates.get(0).getClassification().size());
    }
  }

  /**
   * A canonical id shared by a pathological number of usages - "? bacterium" collects tens of thousands in
   * a large bacterial dataset - must not break the store, and none of them may be dropped: a usage missing
   * from the canonical index gets no stable id reuse in a release.
   */
  @Test
  public void canonicalFanOut() throws IOException {
    final int hotCanonical = 7;
    final int n = 20_000;
    var sink = createSink(2);
    for (int i = 0; i < n; i++) {
      sink.add(snc("u" + i, null, "Aus bus", "Smith", Rank.SPECIES, hotCanonical, hotCanonical));
    }
    try (UsageMatcherStore store = seal(sink)) {
      assertEquals("all usages remain stored", n, store.size());
      assertEquals("they share one canonical bucket", 1, store.canonicalSize());
      assertEquals("and all of them are candidates", n, store.simpleNamesByCanonicalId(hotCanonical).size());
      // every usage is still individually retrievable
      assertEquals("u0", store.get("u0").getId());
      assertEquals("u" + (n - 1), store.get("u" + (n - 1)).getId());
    }
  }

  /**
   * Candidates must not have their parents walked while they are being collected - that walk is what makes
   * a canonical shared by thousands of usages expensive, and nearly all candidates are dropped by the cheap
   * filters before anyone looks at their classification.
   */
  @Test
  public void classificationIsResolvedLazily() throws IOException {
    var sink = createSink(3);
    sink.add(snc("orphan", "gone", "Aus bus", "Smith", Rank.SPECIES, 5, 5));
    try (UsageMatcherStore store = seal(sink)) {
      // collecting the candidates must not resolve anything, or the missing parent would blow up here
      var candidates = store.usagesByCanonicalId(5);
      assertEquals(1, candidates.size());
      assertEquals("orphan", candidates.get(0).getId());
      // only an actual read of the classification walks the parents - and then fails on the missing one
      assertThrows(NotFoundException.class, () -> candidates.get(0).getClassification());
    }
  }

  static <T> java.util.stream.Stream<T> stream(Iterable<T> it) {
    return java.util.stream.StreamSupport.stream(it.spliterator(), false);
  }

  static void assertNotFound(String id, UsageMatcherStore store) {
    try {
      store.get(id);
      fail("Expected NotFoundException for ID " + id);
    } catch (NotFoundException e) {
      // expected
    }
  }

  public SimpleNameCached sncSyn(String id, String parentId, String name, String authorship, Rank rank, int canonicalId, int nidxId) {
    var snc = snc(id, parentId, name, authorship, rank, canonicalId, nidxId);
    snc.setStatus(TaxonomicStatus.SYNONYM);
    return snc;
  }

  public SimpleNameCached snc(String id, String parentId, String name, String authorship, Rank rank, int canonicalId, int nidxId) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parentId);
    sn.setAuthorship(authorship);
    sn.setCanonicalId(canonicalId);
    sn.setNamesIndexId(nidxId);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    return sn;
  }

  /** Creates the sink usages are added to. */
  abstract UsageSink createSink(int datasetKey) throws IOException;

  /** Turns the sink into the store to read from. A no-op for the mutable stores. */
  abstract UsageMatcherStore seal(UsageSink sink) throws IOException;
}
