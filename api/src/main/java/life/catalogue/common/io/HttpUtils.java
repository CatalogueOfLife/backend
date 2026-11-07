package life.catalogue.common.io;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtils implements AutoCloseable {
  private static Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
  private final HttpClient client;
  // true if the client was created here and therefore is closed here too
  private final boolean ownsClient;

  public HttpUtils() {
    this(HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build(), true
    );
  }

  public HttpUtils(HttpClient client) {
    this(client, false);
  }

  private HttpUtils(HttpClient client, boolean ownsClient) {
    this.client = client;
    this.ownsClient = ownsClient;
  }

  /**
   * Closes the http client and its selector thread, unless it was given to this instance.
   */
  @Override
  public void close() {
    if (ownsClient) {
      client.close();
    }
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
