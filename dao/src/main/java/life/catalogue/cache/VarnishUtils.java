package life.catalogue.cache;

import java.net.URI;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VarnishUtils {
  private static final Logger LOG = LoggerFactory.getLogger(VarnishUtils.class);

  public static class HttpBan extends HttpRequestBase {
    public static final String METHOD_NAME = "BAN";

    public HttpBan(URI uri) {
      this.setURI(uri);
    }

    @Override
    public String getMethod() {
      return METHOD_NAME;
    }
  }

  /**
   *
   * @param uri to ban
   * @return status code of response or -1 if an exception occurred
   */
  public static int ban(CloseableHttpClient client, URI uri) {
    HttpBan ban = new HttpBan(uri);

    // execute
    LOG.info("BAN varnish cache at {}", uri);
    try (CloseableHttpResponse response = client.execute(ban)) {
      return response.getStatusLine().getStatusCode();
    } catch (Exception e) {
      LOG.warn("Failed to BAN {}: {}", uri, e.getMessage());
      return -1;
    }
  }
}
