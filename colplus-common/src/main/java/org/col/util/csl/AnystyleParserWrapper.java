package org.col.util.csl;

import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class AnystyleParserWrapper implements AutoCloseable {

  public static synchronized AnystyleParserWrapper getInstance(CloseableHttpClient hc) {
    if (instance == null) {
      instance = new AnystyleParserWrapper(hc);
    }
    return instance;
  }

  public static synchronized AnystyleParserWrapper getInstance() {
    return getInstance(HttpClients.createDefault());
  }

  private static AnystyleParserWrapper instance;

  private final AnystyleWebService svc;
  private final CloseableHttpClient hc;

  private AnystyleParserWrapper(CloseableHttpClient hc) {
    this.svc = new AnystyleWebService();
    this.hc = hc;
  }

  public String parse(String ref) throws ClientProtocolException, IOException, URISyntaxException {
    try (CloseableHttpResponse response = hc.execute(request(ref))) {
      return EntityUtils.toString(response.getEntity());
    }
  }

  /*
   * Really important to make sure this gets called, otherwise the process containing the web
   * service won't get killed
   */
  @Override
  public synchronized void close() {
    this.svc.stop();
    instance = null;
  }

  private static HttpGet request(String reference) throws URISyntaxException {
    URIBuilder ub = new URIBuilder();
    ub.setScheme("http");
    ub.setHost("localhost");
    ub.setPort(AnystyleWebService.HTTP_PORT);
    ub.setParameter(AnystyleWebService.QUERY_PARAM_REF, reference);
    return new HttpGet(ub.build());
  }

}
