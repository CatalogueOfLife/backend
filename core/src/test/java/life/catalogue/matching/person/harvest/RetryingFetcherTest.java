package life.catalogue.matching.person.harvest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class RetryingFetcherTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /** the query service now and then answers 200 with a body cut off, and the API a lag error: both are retried */
  @Test
  public void retriesAnIncompleteAnswer() throws Exception {
    List<String> answers = new ArrayList<>(List.of("{\"results\":{\"bindi", "{\"error\":{\"code\":\"maxlag\"}}", "{\"results\":{}}"));
    var f = new RetryingFetcher(url -> answers.remove(0), Duration.ZERO, Json::complete, 3);
    assertEquals("{\"results\":{}}", f.get("u"));
    assertTrue(answers.isEmpty());
  }

  @Test
  public void givesUpAfterItsRetries() {
    int[] calls = {0};
    var f = new RetryingFetcher(url -> {
      calls[0]++;
      throw new IllegalStateException("HTTP 502");
    }, Duration.ZERO, Json::complete, 3);
    var e = assertThrows(IllegalStateException.class, () -> f.get("u"));
    assertEquals("HTTP 502", e.getMessage());
    assertEquals(3, calls[0]);
  }

  /** a cancelled harvest is interrupted while it waits for an answer: that ends the fetch, it is no failure to retry */
  @Test
  public void anInterruptIsNoRetry() {
    int[] calls = {0};
    var f = new RetryingFetcher(url -> {
      calls[0]++;
      throw new InterruptedException("cancelled");
    }, Duration.ZERO, Json::complete, 3);
    assertThrows(InterruptedException.class, () -> f.get("u"));
    assertEquals(1, calls[0]);
    // the interrupt is not lost for whoever checks the flag
    assertTrue(Thread.interrupted());
  }

  /** an incomplete answer cached by an earlier version must not poison every rerun */
  @Test
  public void anIncompleteCachedAnswerIsFetchedAgain() throws Exception {
    Path dir = tmp.newFolder().toPath();
    new CachingFetcher(dir, url -> "{\"results\":{\"bindi").get("u");
    assertEquals("{\"results\":{}}", new CachingFetcher(dir, url -> "{\"results\":{}}", Json::complete).get("u"));
  }

  @Test
  public void complete() {
    assertTrue(Json.complete("{\"results\":{\"bindings\":[]}}"));
    assertTrue(Json.complete("{\"entities\":{}}"));
    assertFalse(Json.complete("{\"results\":{\"bindi"));
    assertFalse(Json.complete("{\"error\":{\"code\":\"maxlag\",\"info\":\"Waiting for a database server\"}}"));
    assertFalse(Json.complete(""));
    assertFalse(Json.complete("<html>busy</html>"));
  }
}
