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

  /** sources hand over years they could not read as null: they must neither fail nor erase a known year */
  @Test
  public void builderYearsIgnoreNull() {
    var b = new PersonRecord.Builder(life.catalogue.matching.person.Provenance.IPNI);
    b.ipni = "1-1";
    b.born(null);
    b.died(null);
    assertNull(b.build().born());
    b.born(1800);
    b.born(null);
    b.born(1790);
    b.died(1850);
    b.died(null);
    b.died(1855);
    assertEquals(Integer.valueOf(1790), b.build().born());
    assertEquals(Integer.valueOf(1855), b.build().died());
  }

  @Test
  public void recordIdsOwnFirst() {
    var b = new PersonRecord.Builder(life.catalogue.matching.person.Provenance.IPNI);
    b.wikidata = "Q1";
    b.ipni = "1-1";
    assertEquals(List.of("ipni:1-1", "wd:Q1"), List.copyOf(b.build().ids()));
  }
}
