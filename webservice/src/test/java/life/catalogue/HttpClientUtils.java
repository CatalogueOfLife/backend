 package life.catalogue;

 import javax.net.ssl.SSLContext;

 import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
 import org.apache.hc.client5.http.impl.classic.HttpClients;
 import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
 import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
 import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
 import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
 import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
 import org.apache.hc.core5.http.config.Registry;
 import org.apache.hc.core5.http.config.RegistryBuilder;
 import org.apache.hc.core5.ssl.SSLContexts;
 import org.apache.hc.core5.ssl.TrustStrategy;

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
      client = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .build();
    }
    return client;
  }
}
