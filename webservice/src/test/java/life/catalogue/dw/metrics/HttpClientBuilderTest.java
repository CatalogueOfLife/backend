package life.catalogue.dw.metrics;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.setup.Environment;

import io.dropwizard.util.Duration;

import life.catalogue.common.io.DownloadUtil;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HttpClientBuilderTest {

  @Test
  public void simpleName() {
    assertEquals("COLServer", HttpClientBuilder.simpleName("COLServer/da74866_2021-11-05"));
    assertEquals("COLServer", HttpClientBuilder.simpleName("COLServer/da74866 2021-11-05"));
  }

  @Test
  @Ignore
  public void downloadNaturalis() throws IOException {
    var f = File.createTempFile("download", ".zip");
    var env = new Environment("prod");
    // configs we actually use for prod
    var cfg = new JerseyClientConfiguration();
    cfg.setTimeout(Duration.milliseconds(10000));
    cfg.setConnectionTimeout(Duration.milliseconds(80000));
    var tls = new TlsConfiguration();
    tls.setProtocol(null);
    //tls.setSupportedProtocols(List.of("TLSv1.3","TLSv1.2","TLSv1.1","TLSv1"));
    tls.setVerifyHostname(false);
    tls.setTrustSelfSignedCertificates(true);

    cfg.setTlsConfiguration(tls);
    cfg.setGzipEnabled(false);
    cfg.setGzipEnabledForRequests(false);
    cfg.setChunkedEncodingEnabled(false);

    try (var hc = new HttpClientBuilder(env)
      .using(cfg)
      .build("COLServer/da74866 2023-09-21")
    ) {
      DownloadUtil d = new DownloadUtil(hc);
      d.download(URI.create("https://api.biodiversitydata.nl/v2/taxon/dwca/getDataSet/nsr"), f);
      System.out.println(f);
    } finally {
      FileUtils.deleteQuietly(f);
    }
  }
}