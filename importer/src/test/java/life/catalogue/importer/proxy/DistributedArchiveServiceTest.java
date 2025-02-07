package life.catalogue.importer.proxy;

import life.catalogue.common.io.Resources;

import java.io.File;
import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("require github raw to always work. Currently down")
public class DistributedArchiveServiceTest {
  
  static CloseableHttpClient client;
  static DistributedArchiveService das = new DistributedArchiveService(client);
  
  
  @BeforeClass
  public static void init() throws Exception {
    client = HttpClients.createDefault();
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
    das.upload(f);
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