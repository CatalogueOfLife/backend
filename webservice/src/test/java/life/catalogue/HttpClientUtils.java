 package life.catalogue;

 import javax.net.ssl.SSLContext;

 import org.apache.http.config.Registry;
 import org.apache.http.config.RegistryBuilder;
 import org.apache.http.conn.socket.ConnectionSocketFactory;
 import org.apache.http.conn.socket.PlainConnectionSocketFactory;
 import org.apache.http.conn.ssl.NoopHostnameVerifier;
 import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
 import org.apache.http.conn.ssl.SSLContexts;
 import org.apache.http.conn.ssl.TrustStrategy;
 import org.apache.http.impl.client.CloseableHttpClient;
 import org.apache.http.impl.client.HttpClients;
 import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

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
