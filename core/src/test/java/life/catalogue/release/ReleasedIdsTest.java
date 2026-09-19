package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.Test;

import static life.catalogue.release.ReleasedIds.ReleasedId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReleasedIdsTest {

  int counter;
  ReleasedIds ids;

  void init(int max){
    ids = new ReleasedIds();
    counter = 0;
    while (counter < max) {
      ids.add(gen());
    }
  }

  @Test
  @Disabled @Ignore("manual test to check memory footprint")
  public void memory() throws InterruptedException {
    init(1000000);
    System.out.println("DONE");
    TimeUnit.SECONDS.sleep(10);
  }

  @Test
  public void maxFirstKey() throws InterruptedException {
    init(0);
    assertEquals(0, ids.maxKey());
  }

  @Test
  public void addRemove() throws InterruptedException {
    init(10);
    assertEquals(10, ids.size());
    ReleasedId r = ids.byId(3);
    ids.remove(r.id);
    assertNull(ids.byId(3));
    assertEquals(9, ids.size());
    assertEquals(1, r.nxId);
    assertEquals(9, ids.byCanonId(r.nxId).length);
    assertNull(ids.byCanonId(2));
    assertNull(ids.byCanonId(3));

    ids.remove(0);
    assertEquals(8, ids.size());
    assertEquals(8, ids.byCanonId(1).length);
    assertNull(ids.byCanonId(2));
    assertNull(ids.byId(0));

    // we did remove it already, no change
    ids.remove(r.id);
    assertEquals(8, ids.size());
  }

  ReleasedId gen(){
    int id = counter++;
    SimpleNameWithNidx sn = new SimpleNameWithNidx();
    // every ten ids share one names index id
    sn.setNamesIndexId(id / 10 + 1);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.setName("Abies");
    sn.setGroup(TaxGroup.Angiosperms);
    return new ReleasedId(id, counter < 100000 ? 1 : 2, true, sn);
  }
}