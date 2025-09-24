package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.SimpleNameCached;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public abstract class UsageMatcherStoreTestBase {

  static SimpleNameCached add(SimpleNameCached snc, UsageMatcherStore store) {
    store.add(snc);
    return new SimpleNameCached(snc);
  }

  @Test
  public void basics() throws IOException {
    try (UsageMatcherStore store = createStore(1)) {
      assertEquals(0, store.size());

      var sn0 = add(snc("0", null, "Ausaceae", null, Rank.FAMILY, 0, 10), store);
      var sn1 = add(snc("1", "4", "Aus bus", "Smith", Rank.SPECIES, 1, 11), store);
      var sn2 = add(snc("2", "4", "Aus cus", "Miller", Rank.SPECIES, 2, 12), store);
      var sn3 = add(snc("3", "1", "Aus bus cus", "(Miller)", Rank.SUBSPECIES, 3, 13), store);
      var sn4 = add(snc("4", "0", "Aus", "Green", Rank.GENUS, 4, 14), store);
      var sn5 = add(sncSyn("5", "2", "Aus cus", "Jackson", Rank.SPECIES, 2, 15), store);

      assertEquals(6, store.size());

      assertEquals(sn2, store.get(sn2.getId()));
      var cl = store.getClassification(sn5.getParentId());
      assertEquals(3, cl.size());
      assertEquals(sn5.getParentId(), cl.get(0).getId());

      // expected mutations when the ID 4 changes...
      sn4.setId("44");
      sn1.setParent(sn4.getId());
      sn2.setParent(sn4.getId());
      store.updateUsageID("4", sn4.getId());

      assertEquals(sn2, store.get(sn2.getId()));
      cl = store.getClassification(sn5.getParentId());
      assertEquals(3, cl.size());
      assertEquals(sn5.getParentId(), cl.get(0).getId());

      assertEquals(sn4, store.get(sn4.getId()));
      assertNotFound("4", store);
    }
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
    return sn;
  }


  abstract UsageMatcherStore createStore(int datasetKey) throws IOException;

}