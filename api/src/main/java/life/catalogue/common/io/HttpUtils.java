package life.catalogue.common.io;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtils {
  private static Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
  private final HttpClient client;

  public HttpUtils() {
    this(HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build()
    );
  }

  public HttpUtils(HttpClient client) {
    this.client = client;
  }

  /**
   * Retrieves the content of the URL doing a GET request.
   * Returns the content in case of any 2xx response, but throws an IOException otherwise.
   * @param url
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  public String get(URI url) throws IOException, InterruptedException {
    var req = HttpRequest.newBuilder(url);
    req.header("User-Agent", "ChecklistBank/1.0");
    req.header("Cache-Control", "no-cache");
    var resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    LOG.info("Response {} for GET {}", resp.statusCode(), url);
    if (resp.statusCode() / 100 == 2) {
      return resp.body();
    }
    throw new IOException("GET request "+ url + " failed with http "+ resp.statusCode());
  }

}
