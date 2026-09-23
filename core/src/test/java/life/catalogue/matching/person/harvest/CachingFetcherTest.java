package life.catalogue.matching.person.harvest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class CachingFetcherTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** a harvest that dies halfway resumes from what it fetched before */
  @Test
  public void servesFromDiskAcrossRuns() throws Exception {
    Path dir = tmp.newFolder().toPath();
    List<String> asked = new ArrayList<>();
    Fetcher net = url -> {
      asked.add(url);
      return "body of " + url;
    };
    assertEquals("body of a", new CachingFetcher(dir, net).get("a"));
    assertEquals("body of a", new CachingFetcher(dir, net).get("a"));
    assertEquals("body of b", new CachingFetcher(dir, net).get("b"));
    assertEquals(List.of("a", "b"), asked);
  }

  @Test
  public void aFailureIsNotCached() throws Exception {
    Path dir = tmp.newFolder().toPath();
    Fetcher failing = url -> {
      throw new IllegalStateException("HTTP 429");
    };
    assertThrows(IllegalStateException.class, () -> new CachingFetcher(dir, failing).get("a"));
    assertEquals("ok", new CachingFetcher(dir, url -> "ok").get("a"));
  }

  @Test
  public void recordIdsOwnFirst() {
    var b = new PersonRecord.Builder(life.catalogue.matching.person.Provenance.IPNI);
    b.wikidata = "Q1";
    b.ipni = "1-1";
    assertEquals(List.of("ipni:1-1", "wd:Q1"), List.copyOf(b.build().ids()));
  }
}
