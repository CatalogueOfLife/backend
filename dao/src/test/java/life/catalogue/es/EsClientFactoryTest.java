package life.catalogue.es;

import life.catalogue.config.EsConfig;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The rest5 client defaults to a 30 s socket timeout and, on hitting it, marks the node dead and replays the request on
 * the next node. Under load every slow search was therefore submitted again. The configured socket timeout has to reach
 * the connection config, not only the response timeout.
 */
public class EsClientFactoryTest {

  @Test
  public void socketTimeoutReachesTheConnection() {
    EsConfig cfg = new EsConfig();
    cfg.socketTimeout = 123_000;
    cfg.connectTimeout = 4_000;

    ConnectionConfig.Builder b = ConnectionConfig.custom();
    EsClientFactory.configureConnection(cfg, b);
    ConnectionConfig cc = b.build();

    assertEquals(Timeout.ofMilliseconds(4_000), cc.getConnectTimeout());
    assertEquals(Timeout.ofMilliseconds(123_000), cc.getSocketTimeout());
  }

  @Test
  public void socketTimeoutBoundsTheResponse() {
    EsConfig cfg = new EsConfig();
    cfg.socketTimeout = 123_000;

    RequestConfig.Builder b = RequestConfig.custom();
    EsClientFactory.configureRequest(cfg, b);
    RequestConfig rc = b.build();

    assertEquals(Timeout.ofMilliseconds(123_000), rc.getResponseTimeout());
  }
}
