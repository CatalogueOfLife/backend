package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DownloadUtilTest {
  CloseableHttpClient hc;
  File f;

  @Before
  public void start() throws IOException {
    hc = HttpClientBuilder.create()
      .setRedirectStrategy(DefaultRedirectStrategy.INSTANCE)
      .disableRedirectHandling()
      .build();
    f = File.createTempFile("download", ".zip");
    System.out.println(f);
  }
  
  @After
  public void stop() throws IOException {
    FileUtils.deleteQuietly(f);
    hc.close();
  }

  @Test
  @Disabled @Ignore("manual only")
  public void testBdjRateLimiting() throws IOException, ExecutionException, InterruptedException {
    DownloadUtil d = new DownloadUtil(hc);
    var keys = new int[]{94202,98935,96601};
    int attempt = 0;
    File dir = new File("/Users/markus/Downloads/bdj");
    if (dir.exists()) {
      FileUtils.cleanDirectory(dir);
    } else {
      dir.mkdir();
    }
    List<Future<Boolean>> jobs = new ArrayList<>();
    while (attempt<100) {
      for (var key : keys) {
        attempt++;
        File down = new File(dir, attempt+"-"+key+".zip");
        var uri = URI.create("https://bdj.pensoft.net/lib/ajax_srv/archive_download.php?archive_type=2&document_id="+key);
        jobs.add(CompletableFuture.supplyAsync(() -> download(d, uri, down)));
      }
    }

    for (var f : jobs) {
      if (!f.get()) {
        System.out.println("Failed job");
      }
    }
  }

  @Test
  @Disabled @Ignore("manual debugging")
  public void plazi() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    d.download(URI.create("https://tb.plazi.org/GgServer/dwca/1C5A0163FFCBF3266052EB7D4D70FFF7.zip"), f);
  }

  @Test
  @Disabled @Ignore("manual debugging")
  public void github() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    d.download(URI.create("https://github.com/mdoering/hominidae/archive/refs/heads/main.zip"), f);
  }

  boolean download(DownloadUtil d, URI uri, File down) {
    try {
      d.download(uri, down);
      return true;
    } catch (DownloadException e) {
      System.out.println(e);
      return false;
    }
  }
  @Test(expected = DownloadException.class)
  public void downloadFail() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    // a private repo should give a 404
    d.download(URI.create("https://github.com/Sp2000/data-scarabs/archive/master.zip"), f);
  }

  @Test
  @Disabled @Ignore
  public void downloadFtp() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    d.download(URI.create("ftp://ftp.ebi.ac.uk/pub/databases/ena/taxonomy/sdwca.zip"), f);
  }

  /**
   * https://github.com/CatalogueOfLife/checklistbank/issues/1295
   */
  @Test
  @Disabled @Ignore
  public void downloadNaturalis() throws IOException {
    DownloadUtil d = new DownloadUtil(hc);
    d.download(URI.create("https://api.biodiversitydata.nl/v2/taxon/dwca/getDataSet/nsr"), f);
  }

  @Test
  @Disabled @Ignore
  public void downloadWithToken() throws IOException {
    DownloadUtil d = new DownloadUtil(hc, "xxx", "gdToken");
    // a private repo should be accessible with the right API token
    d.download(URI.create("https://github.com/gbif/algae/archive/master.zip"), f);
    //d.download(URI.create("https://github.com/CatalogueOfLife/data/raw/master/ACEF/10.tar.gz"), f);
    //d.download(URI.create("https://github.com/CatalogueOfLife/data-scarabs/archive/master.zip"), f);
    //d.download(URI.create("https://github.com/CatalogueOfLife/data-world-spider-catalog/archive/master.zip"), f);
    //d.download(URI.create("https://github.com/gdower/data-cycads/archive/master.zip"), f);
  }

  /**
   * The GitHub tokens go to the exact GitHub hosts over https only, see gbif/informatics#85 (B5).
   * Redirects build their request from their own url, so they follow the same rule.
   */
  @Test
  public void githubToken() {
    DownloadUtil d = new DownloadUtil(hc, "TOKEN", "GEOFF");
    assertEquals("TOKEN", d.githubToken(URI.create("https://github.com/CatalogueOfLife/data-scarabs/archive/master.zip")));
    assertEquals("TOKEN", d.githubToken(URI.create("https://codeload.github.com/CatalogueOfLife/data-scarabs/zip/master")));
    assertEquals("TOKEN", d.githubToken(URI.create("https://raw.githubusercontent.com/CatalogueOfLife/coldp/master/metadata.yaml")));
    assertEquals("TOKEN", d.githubToken(URI.create("https://GitHub.com/x/y")));
    assertEquals("GEOFF", d.githubToken(URI.create("https://github.com/gdower/data-cycads/archive/master.zip")));

    assertNull(d.githubToken(URI.create("http://github.com/x/y")));
    assertNull(d.githubToken(URI.create("https://github.com.example.org/x/y")));
    assertNull(d.githubToken(URI.create("https://evilgithub.com/x/y")));
    assertNull(d.githubToken(URI.create("https://githubusercontent.com.example.org/x")));
    assertNull(d.githubToken(URI.create("https://pages.github.com.evil/x")));
    assertNull(d.githubToken(URI.create("https://example.org/github.com/x")));

    assertNull(new DownloadUtil(hc).githubToken(URI.create("https://github.com/x/y")));
  }
}
