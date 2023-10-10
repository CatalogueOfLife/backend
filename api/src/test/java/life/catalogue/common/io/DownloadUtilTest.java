package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import life.catalogue.api.vocab.Environment;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class DownloadUtilTest {
  CloseableHttpClient hc;
  File f;

  @Before
  public void start() throws IOException {
    hc = HttpClientBuilder.create().build();
    f = File.createTempFile("download", ".zip");
    System.out.println(f);
  }
  
  @After
  public void stop() throws IOException {
    FileUtils.deleteQuietly(f);
    hc.close();
  }
  
  @Test(expected = DownloadException.class)
  public void downloadFail() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    // a private repo should give a 404
    d.download(URI.create("https://github.com/Sp2000/data-scarabs/archive/master.zip"), f);
  }

  @Test
  @Ignore
  public void downloadFtp() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    d.download(URI.create("ftp://ftp.ebi.ac.uk/pub/databases/ena/taxonomy/sdwca.zip"), f);
  }

  /**
   * https://github.com/CatalogueOfLife/checklistbank/issues/1295
   */
  @Test
  @Ignore
  public void downloadNaturalis() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    d.download(URI.create("https://api.biodiversitydata.nl/v2/taxon/dwca/getDataSet/nsr"), f);
  }

  @Test
  @Ignore
  public void downloadWithToken() throws IOException {
    DownloadUtil d = new DownloadUtil(hc, "xxx", "gdToken");
    // a private repo should be accessible with the right API token
    d.download(URI.create("https://github.com/gbif/algae/archive/master.zip"), f);
    //d.download(URI.create("https://github.com/CatalogueOfLife/data/raw/master/ACEF/10.tar.gz"), f);
    //d.download(URI.create("https://github.com/CatalogueOfLife/data-scarabs/archive/master.zip"), f);
    //d.download(URI.create("https://github.com/CatalogueOfLife/data-world-spider-catalog/archive/master.zip"), f);
    //d.download(URI.create("https://github.com/gdower/data-cycads/archive/master.zip"), f);
  }
}