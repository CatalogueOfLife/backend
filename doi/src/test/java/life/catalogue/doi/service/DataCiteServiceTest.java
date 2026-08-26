package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;

import jakarta.ws.rs.client.ClientBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests that do not talk to DataCite - see DataCiteServiceIT for the manual, live ones.
 */
public class DataCiteServiceTest {

  /** the real body DataCite sends when a DOI is POSTed twice */
  static final String TAKEN = "{\"errors\":[{\"source\":\"doi\",\"title\":\"This DOI has already been taken\",\"uid\":\"10.48580/dgyxm.v1\"}]}";

  @Test
  public void alreadyTaken() {
    assertTrue(DataCiteService.isAlreadyTaken(TAKEN));
    // uppercase title, as we match case insensitively
    assertTrue(DataCiteService.isAlreadyTaken("{\"errors\":[{\"title\":\"This DOI HAS ALREADY BEEN TAKEN\"}]}"));
    // several errors, one of them ours
    assertTrue(DataCiteService.isAlreadyTaken("{\"errors\":[{\"title\":\"Whatever\"},{\"title\":\"This DOI has already been taken\"}]}"));
    // not JSON at all, but says so
    assertTrue(DataCiteService.isAlreadyTaken("This DOI has already been taken"));
  }

  @Test
  public void notAlreadyTaken() {
    assertFalse(DataCiteService.isAlreadyTaken(null));
    assertFalse(DataCiteService.isAlreadyTaken(""));
    assertFalse(DataCiteService.isAlreadyTaken("   "));
    // a real 422 about bad metadata must NOT be mistaken for a duplicate
    assertFalse(DataCiteService.isAlreadyTaken("{\"errors\":[{\"source\":\"creators\",\"title\":\"This element is not expected\"}]}"));
    assertFalse(DataCiteService.isAlreadyTaken("{\"errors\":[]}"));
    assertFalse(DataCiteService.isAlreadyTaken("<html><body>502 Bad Gateway</body></html>"));
  }

  /**
   * The DOI jobs run concurrently on virtual threads. The one mail per minute throttle has to hold when a
   * whole burst of DOIs fails at the same instant - which is exactly the case that used to leak mails.
   */
  @Test
  public void notifyExceptionThrottledUnderConcurrency() throws Exception {
    var cfg = new DoiConfig();
    cfg.api = "https://api.test.datacite.org";
    cfg.username = "user";
    cfg.password = "pass";
    var mailer = mock(Mailer.class);
    var service = new DataCiteService(cfg, ClientBuilder.newClient(), mailer, "to@col.org", "from@col.org");

    final int threads = 16;
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(threads);
    ExecutorService exec = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        final int idx = i;
        exec.submit(() -> {
          try {
            start.await();
            service.notifyException(DOI.test("d" + idx + ".v1"), "CREATE", new DoiException("boom"));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }
      start.countDown();
      assertTrue(done.await(30, TimeUnit.SECONDS), "notifications did not finish");
    } finally {
      exec.shutdownNow();
    }

    verify(mailer, times(1)).sendMail(any(Email.class), anyBoolean());
  }
}
