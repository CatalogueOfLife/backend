package life.catalogue.matching.person.harvest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fetches politely: a pause before every request, growing with each retry, so a rate limit (HTTP 429) or a
 * timeout of the Wikidata query service costs a wait, not the run.
 */
public class HttpFetcher implements Fetcher {
  private static final String USER_AGENT = "col-backend-person-harvest/1.0 (https://www.checklistbank.org)";
  private static final int MAX_RETRIES = 6;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  private final String accept;
  private final Duration pause;

  public HttpFetcher(String accept, Duration pause) {
    this.accept = accept;
    this.pause = pause;
  }

  @Override
  public String get(String url) throws Exception {
    Exception last = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      Thread.sleep(pause.toMillis() * attempt * attempt);
      try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .header("Accept", accept)
          .header("User-Agent", USER_AGENT)
          .timeout(Duration.ofMinutes(3)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 200) {
          return resp.body();
        }
        last = new IllegalStateException("HTTP " + resp.statusCode() + " for " + url);
      } catch (Exception e) {
        last = e;
      }
      System.err.println("  fetch attempt " + attempt + "/" + MAX_RETRIES + " failed: " + last.getMessage());
    }
    throw last;
  }
}
