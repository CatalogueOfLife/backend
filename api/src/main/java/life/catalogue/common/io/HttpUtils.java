package life.catalogue.common.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpUtils {
  private static Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
  private final HttpClient client;

  public HttpUtils() {
    this.client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.ALWAYS)
      .build();
  }

  public String get(URI url) throws IOException, InterruptedException {
    var req = HttpRequest.newBuilder(url);
    req.header("User-Agent", "ChecklistBank/1.0");
    return client.send(req.build(), HttpResponse.BodyHandlers.ofString()).body();
  }

}
