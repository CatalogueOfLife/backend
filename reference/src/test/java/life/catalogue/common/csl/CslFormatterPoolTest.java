package life.catalogue.common.csl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CslFormatterPoolTest {

  static CSLItemDataBuilder item(String title) {
    return new CSLItemDataBuilder()
      .type(CSLType.ARTICLE_JOURNAL)
      .title(title)
      .author("Karl", "Marx")
      .containerTitle("Proceedings of Nature in Space")
      .volume(42)
      .issued(1999, 5, 12);
  }

  @Test
  public void sameAsSingleFormatter() {
    var single = new CslFormatter(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT);
    var pool = new CslFormatterPool(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT, 3);
    assertEquals(3, pool.size());
    // more citations than formatters so the rotation wraps at least twice
    for (int i = 0; i < 10; i++) {
      var csl = item("Title " + i).build();
      assertEquals(single.cite(csl), pool.cite(csl));
    }
  }

  /**
   * The whole point of the pool is that several threads can render at once. Each item carries its own title,
   * so any cross talk between the formatters' shared item providers shows up as a citation quoting the wrong one.
   */
  @Test
  public void concurrent() throws Exception {
    final int threads = 8;
    final int perThread = 25;
    var pool = new CslFormatterPool(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT, 4);
    var exec = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        final int thread = t;
        tasks.add(() -> {
          for (int i = 0; i < perThread; i++) {
            String title = "Thread " + thread + " item " + i;
            String cite = pool.cite(item(title).build());
            assertTrue("expected " + title + " in " + cite, cite.contains(title));
          }
          return null;
        });
      }
      for (Future<Void> f : exec.invokeAll(tasks)) {
        f.get(); // rethrows any assertion failure from the worker
      }
    } finally {
      exec.shutdownNow();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyPool() {
    new CslFormatterPool(CslFormatter.STYLE.APA, CslFormatter.FORMAT.TEXT, 0);
  }
}
