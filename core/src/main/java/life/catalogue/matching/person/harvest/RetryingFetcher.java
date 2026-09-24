package life.catalogue.matching.person.harvest;

import java.time.Duration;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches politely and patiently: a pause before every request, growing with each retry, and an answer that fails
 * the check counts as a failure. A rate limit, a timeout or a body cut off costs a wait, not the run.
 */
public class RetryingFetcher implements Fetcher {
  private static final Logger LOG = LoggerFactory.getLogger(RetryingFetcher.class);
  private static final int MAX_RETRIES = 6;
  private final Fetcher delegate;
  private final Duration pause;
  private final Predicate<String> valid;
  private final int retries;

  public RetryingFetcher(Fetcher delegate, Duration pause, Predicate<String> valid) {
    this(delegate, pause, valid, MAX_RETRIES);
  }

  RetryingFetcher(Fetcher delegate, Duration pause, Predicate<String> valid, int retries) {
    this.delegate = delegate;
    this.pause = pause;
    this.valid = valid;
    this.retries = retries;
  }

  @Override
  public String get(String url) throws Exception {
    Exception last = null;
    for (int attempt = 1; attempt <= retries; attempt++) {
      Thread.sleep(pause.toMillis() * attempt * attempt);
      try {
        String body = delegate.get(url);
        if (valid.test(body)) {
          return body;
        }
        last = new IllegalStateException("incomplete answer for " + StringUtils.abbreviate(url, 200));
      } catch (InterruptedException e) {
        // a cancelled job: stop fetching, and keep the flag for whoever checks it
        Thread.currentThread().interrupt();
        throw e;
      } catch (Exception e) {
        last = e;
      }
      LOG.warn("Fetch attempt {}/{} failed: {}", attempt, retries, StringUtils.abbreviate(last.getMessage(), 300));
    }
    throw last;
  }
}
