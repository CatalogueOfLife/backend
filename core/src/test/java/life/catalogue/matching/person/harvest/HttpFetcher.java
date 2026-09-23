package life.catalogue.matching.person.harvest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;

/**
 * One GET, anything but HTTP 200 is an exception. {@link RetryingFetcher} decides what happens then.
 */
public class HttpFetcher implements Fetcher {
  private static final String USER_AGENT = "col-backend-person-harvest/1.0 (https://www.checklistbank.org)";
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  private final String accept;

  public HttpFetcher(String accept) {
    this.accept = accept;
  }

  @Override
  public String get(String url) throws Exception {
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
      .header("Accept", accept)
      .header("User-Agent", USER_AGENT)
      .timeout(Duration.ofMinutes(3)).GET().build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (resp.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + resp.statusCode() + " for " + StringUtils.abbreviate(url, 200));
    }
    return resp.body();
  }
}
