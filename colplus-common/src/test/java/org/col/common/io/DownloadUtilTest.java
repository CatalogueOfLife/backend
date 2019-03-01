package org.col.common.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class DownloadUtilTest {
  CloseableHttpClient hc;
  
  @Before
  public void start() {
    hc = HttpClientBuilder.create().build();
  }
  
  @After
  public void stop() throws IOException {
    hc.close();
  }
  
  @Test(expected = DownloadException.class)
  public void downloadFail() throws IOException {
    File f = File.createTempFile("download", ".zip");
    DownloadUtil d = new DownloadUtil(hc);
    // a private repo should give a 404
    d.download(URI.create("https://github.com/Sp2000/data-scarabs/archive/master.zip"), f);
  }
  
  @Test
  @Ignore
  public void downloadWithToken() throws IOException {
    File f = File.createTempFile("download", ".zip");
    DownloadUtil d = new DownloadUtil(hc, "ATTACH-ME", null);
    // a private repo should be accessible with the right API token
    d.download(URI.create("https://github.com/gdower/data-cycads/archive/master.zip"), f);
  }
}