package life.catalogue.dao;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.ObjectCache;
import life.catalogue.cache.UsageCache;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A parent cycle in the data must never turn the upward classification walk into an endless loop.
 * Before the cycle guard existed such a cycle grew a single ArrayList to ~95 million SimpleNames
 * and killed the rw server with an OOM, see the 2026-09-02 heap dump.
 */
public class NameUsageProcessorCycleTest {
  static final int DATASET_KEY = 77;

  static Taxon taxon(String id, String name, String parentId) {
    Name n = new Name();
    n.setDatasetKey(DATASET_KEY);
    n.setId(id);
    n.setScientificName(name);
    n.setRank(Rank.SPECIES);
    Taxon t = new Taxon(n);
    t.setDatasetKey(DATASET_KEY);
    t.setId(id);
    t.setParentId(parentId);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    return t;
  }

  static SimpleNameCached cached(String id, String name, String parentId) {
    var sn = new SimpleNameCached(id, name, Rank.SPECIES);
    sn.setParent(parentId);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    return sn;
  }

  static final CacheLoader NO_LOADER = new CacheLoader() {
    @Override
    public SimpleNameCached load(String key) {
      return null;
    }

    @Override
    public void commit() {
    }
  };

  /**
   * The exact shape seen in production: A and B are each others parent, A is served from the taxa
   * cache (yielding a fresh SimpleName), B from the usage cache (yielding a SimpleNameCached), so
   * every turn of the loop appended one of each.
   */
  @Test(timeout = 30_000)
  public void mixedTwoNodeCycle() {
    var taxa = ObjectCache.<NameUsageWrapper>hashMap();
    var usageCache = UsageCache.hashMap(DATASET_KEY);

    taxa.put(new NameUsageWrapper(taxon("A", "Prunus domestica insititia", "B")));
    usageCache.put(cached("B", "Prunus domestica subsp. insititia", "A"));

    var nuw = new NameUsageWrapper(taxon("X", "Prunus domestica insititia var. nigra", "A"));
    new NameUsageProcessor(null, null).addClassification(nuw, taxa, usageCache, NO_LOADER);

    // X, A and B, each exactly once, the usage itself last
    assertEquals(List.of("B", "A", "X"), nuw.getClassification().stream().map(SimpleName::getId).toList());
  }

  /**
   * A cycle that does not include the usage we start from must still terminate.
   */
  @Test(timeout = 30_000)
  public void cycleAboveTheUsage() {
    var taxa = ObjectCache.<NameUsageWrapper>hashMap();
    var usageCache = UsageCache.hashMap(DATASET_KEY);

    taxa.put(new NameUsageWrapper(taxon("A", "Aname", "B")));
    taxa.put(new NameUsageWrapper(taxon("B", "Bname", "C")));
    taxa.put(new NameUsageWrapper(taxon("C", "Cname", "A")));

    var nuw = new NameUsageWrapper(taxon("X", "Xname", "A"));
    new NameUsageProcessor(null, null).addClassification(nuw, taxa, usageCache, NO_LOADER);

    assertEquals(List.of("C", "B", "A", "X"), nuw.getClassification().stream().map(SimpleName::getId).toList());
  }

  /**
   * A usage that is its own parent is the degenerate case of the same bug.
   */
  @Test(timeout = 30_000)
  public void selfLoop() {
    var taxa = ObjectCache.<NameUsageWrapper>hashMap();
    var usageCache = UsageCache.hashMap(DATASET_KEY);

    var nuw = new NameUsageWrapper(taxon("X", "Xname", "X"));
    taxa.put(nuw);
    new NameUsageProcessor(null, null).addClassification(nuw, taxa, usageCache, NO_LOADER);

    assertEquals(List.of("X"), nuw.getClassification().stream().map(SimpleName::getId).toList());
  }

  /**
   * An acyclic but absurdly deep chain is capped too, so a broken tree cannot eat the heap either.
   */
  @Test(timeout = 30_000)
  public void depthCap() {
    var taxa = ObjectCache.<NameUsageWrapper>hashMap();
    var usageCache = UsageCache.hashMap(DATASET_KEY);

    final int depth = NameUsageProcessor.MAX_CLASSIFICATION_DEPTH * 3;
    for (int i = 0; i < depth; i++) {
      taxa.put(new NameUsageWrapper(taxon("p" + i, "Name" + i, i + 1 < depth ? "p" + (i + 1) : null)));
    }

    var nuw = new NameUsageWrapper(taxon("X", "Xname", "p0"));
    new NameUsageProcessor(null, null).addClassification(nuw, taxa, usageCache, NO_LOADER);

    assertEquals(NameUsageProcessor.MAX_CLASSIFICATION_DEPTH, nuw.getClassification().size());
    assertTrue(nuw.getClassification().get(nuw.getClassification().size() - 1).getId().equals("X"));
  }

  /**
   * The ordinary, acyclic case must be untouched by the guard.
   */
  @Test
  public void normalClassification() {
    var taxa = ObjectCache.<NameUsageWrapper>hashMap();
    var usageCache = UsageCache.hashMap(DATASET_KEY);

    taxa.put(new NameUsageWrapper(taxon("g1", "Prunus", null)));
    usageCache.put(cached("f1", "Rosaceae", null));
    taxa.put(new NameUsageWrapper(taxon("s1", "Prunus domestica", "g1")));

    var nuw = new NameUsageWrapper(taxon("ssp1", "Prunus domestica insititia", "s1"));
    new NameUsageProcessor(null, null).addClassification(nuw, taxa, usageCache, NO_LOADER);

    assertEquals(List.of("g1", "s1", "ssp1"), nuw.getClassification().stream().map(SimpleName::getId).toList());
  }
}
