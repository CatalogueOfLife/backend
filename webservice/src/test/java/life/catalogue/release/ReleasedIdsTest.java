package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static life.catalogue.release.ReleasedIds.ReleasedId;

public class ReleasedIdsTest {
  int counter;
  ReleasedIds ids = new ReleasedIds();

  void init(int max){
    counter = 0;
    while (counter < max) {
      ids.add(gen());
    }
  }

  @Test
  public void memory() throws InterruptedException {
    init(1000000);
    System.out.println("DONE");
    TimeUnit.SECONDS.sleep(10);
  }


  ReleasedId gen(){
    int id = counter++;
    int[] nxIds;
    if (id % 1000 == 0) {
      nxIds = new int[]{id % 1000, id % 1000+1, id % 1000+2};

    } else if (id % 100 == 0) {
      nxIds = new int[]{id % 1000, id % 1000+1};

    } else {
      nxIds = new int[]{id % 1000};
    }
    return new ReleasedId(id, nxIds, counter < 100000 ? 1 : 2);
  }
}