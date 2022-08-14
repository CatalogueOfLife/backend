package life.catalogue.importer;

import life.catalogue.api.vocab.Users;

import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ImportRequestTest {
  
  @Test
  public void compareTo() throws Exception {
    ImportRequest r1 = new ImportRequest(1,false, false);
    TimeUnit.MILLISECONDS.sleep(10);
    ImportRequest r2 = new ImportRequest(2, false, true);
    TimeUnit.MILLISECONDS.sleep(10);
    ImportRequest r3 = new ImportRequest(3, true, false);
    TimeUnit.MILLISECONDS.sleep(10);
    ImportRequest r4 = new ImportRequest(4, false, true);
  
    PriorityBlockingQueue<ImportRequest> queue = new PriorityBlockingQueue<>();
    queue.add(r1);
    queue.add(r2);
    queue.add(r3);
    queue.add(r4);
    
    assertEquals(4 ,queue.size());
    List<ImportRequest> expected = Lists.newArrayList(r2, r4, r1, r3);

    for (ImportRequest ex : expected) {
      assertFalse(queue.isEmpty());
      assertEquals(ex, queue.poll());
    }
  }
}