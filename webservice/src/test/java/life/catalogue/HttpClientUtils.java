 package life.catalogue;

 import javax.net.ssl.SSLContext;

 import org.apache.hc.core5.http.config.Registry;
 import org.apache.hc.core5.http.config.RegistryBuilder;
 import org.apache.hc.core5.http.conn.socket.ConnectionSocketFactory;
 import org.apache.hc.core5.http.conn.socket.PlainConnectionSocketFactory;
 import org.apache.hc.core5.http.conn.ssl.NoopHostnameVerifier;
 import org.apache.hc.core5.http.conn.ssl.SSLConnectionSocketFactory;
 import org.apache.hc.core5.http.conn.ssl.SSLContexts;
 import org.apache.hc.core5.http.conn.ssl.TrustStrategy;
 import org.apache.hc.core5.http.impl.client.CloseableHttpClient;
 import org.apache.hc.core5.http.impl.client.HttpClients;
 import org.apache.hc.core5.http.impl.conn.BasicHttpClientConnectionManager;

public class HttpClientUtils {
  private static CloseableHttpClient client;

  public static CloseableHttpClient httpsClient() throws Exception {
    if (client == null) {
      TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
      SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
      SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
    
      Registry<ConnectionSocketFactory> socketFactoryRegistry =
          RegistryBuilder.<ConnectionSocketFactory> create()
              .register("https", sslsf)
              .register("http", new PlainConnectionSocketFactory())
              .build();
    
      BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);
      CloseableHttpClient httpClient = HttpClients.custom()
          .setSSLSocketFactory(sslsf)
          .setConnectionManager(connectionManager).build();
      
      client = httpClient;
    }
    return client;
  }
}
