package life.catalogue.importer.proxy;

import java.io.File;
import java.net.URI;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import life.catalogue.WsServerConfig;
import life.catalogue.WsServerConfigTest;
import life.catalogue.common.io.Resources;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

//@Ignore("manual tests depending on proxied external sources")
public class DistributedArchiveServiceTest {
  
  static CloseableHttpClient client;
  static DistributedArchiveService das = new DistributedArchiveService(client);
  
  
  @BeforeClass
  public static void init() throws Exception {
    final WsServerConfig cfg = WsServerConfigTest.readTestConfig();
    client = new HttpClientBuilder(new MetricRegistry())
        .using(cfg.client)
        .build("local");
    das = new DistributedArchiveService(client);
  }
  
  @AfterClass
  public static void stop() throws Exception {
    client.close();
  }
  
  @Test
  public void uploaded() throws Exception {
    File f = Resources.tmpCopy("proxy/1011.yaml");
    System.out.println(f.getAbsolutePath());
    das.uploaded(f);
    f.delete();
  }
  
  @Test
  public void download() throws Exception {
    File zip = File.createTempFile("colDA-", ".zip");
    System.out.println(zip.getAbsolutePath());
    das.download(URI.create("https://raw.githubusercontent.com/CatalogueOfLife/backend/master/webservice/src/test/resources/proxy/1011.yaml"), zip);
    zip.delete();
  }
}