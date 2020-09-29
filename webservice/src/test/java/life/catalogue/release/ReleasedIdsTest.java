package life.catalogue.release;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

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
  @Ignore("manual test to check memory footprint")
  public void memory() throws InterruptedException {
    init(1000000);
    System.out.println("DONE");
    TimeUnit.SECONDS.sleep(10);
  }


  @Test
  public void addRemove() throws InterruptedException {
    init(10);
    assertEquals(10, ids.size());
    ReleasedId r = ids.byId(3);
    ids.remove(r.id);
    assertNull(ids.byId(3));
    assertEquals(9, ids.size());
    assertEquals(3, r.nxId);
    assertEquals(1, ids.byNxId(0).length);
    assertEquals(2, ids.byNxId(1).length);
    assertEquals(2, ids.byNxId(2).length);
    assertNull(ids.byNxId(3));

    // id 0 has 0,1,2 as nx ids
    r = ids.byId(1);
    ids.remove(r.id);
    assertEquals(8, ids.size());
    assertEquals(1, ids.byNxId(0).length);
    assertEquals(1, ids.byNxId(1).length);
    assertNull(ids.byId(1));

    // we did remove it already, no change
    ids.remove(r.id);
    assertEquals(8, ids.size());
  }


  ReleasedId gen(){
    int id = counter++;
    int nxId;
    if (id % 1000 == 0) {
      nxId = id % 1000+2;
    } else if (id % 100 == 0) {
      nxId = id % 1000+1;
    } else {
      nxId = id % 1000;
    }
    return new ReleasedId(id, nxId, counter < 100000 ? 1 : 2);
  }
}