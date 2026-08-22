package life.catalogue.importer;

import life.catalogue.api.jackson.ApiModule;

import java.net.URI;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.collect.Lists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class ImportRequestTest {

  @Test
  public void compareTo() throws Exception {
    ImportRequest r1 = new ImportRequest(1,false, false, null);
    TimeUnit.MILLISECONDS.sleep(10);
    ImportRequest r2 = new ImportRequest(2, false, true, null);
    TimeUnit.MILLISECONDS.sleep(10);
    ImportRequest r3 = new ImportRequest(3, true, false, null);
    TimeUnit.MILLISECONDS.sleep(10);
    ImportRequest r4 = new ImportRequest(4, false, true, null);

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

  @Test
  public void callbackJson() throws Exception {
    // callback binds from the JSON body (used by the POST /importer schedule endpoint)
    ImportRequest req = ApiModule.MAPPER.readValue(
      "{\"datasetKey\":1000, \"force\":true, \"callback\":\"https://example.org/hook\"}", ImportRequest.class);
    assertEquals(1000, req.datasetKey);
    assertEquals(URI.create("https://example.org/hook"), req.callback);

    // absent callback stays null
    ImportRequest none = ApiModule.MAPPER.readValue("{\"datasetKey\":1000}", ImportRequest.class);
    assertNull(none.callback);
  }
}
