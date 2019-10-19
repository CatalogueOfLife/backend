package org.col.importer.yaml;

import java.io.File;

import org.apache.http.impl.client.CloseableHttpClient;
import org.col.HttpClientUtils;
import org.col.common.io.Resources;
import org.junit.Test;

public class DistributedArchiveServiceTest {
  
  @Test
  public void uploaded() throws Exception {
    CloseableHttpClient client = HttpClientUtils.httpsClient();
    DistributedArchiveService das = new DistributedArchiveService(client);
    File f = Resources.tmpCopy("yaml/archive.yaml");
    System.out.println(f.getAbsolutePath());
    das.uploaded(f);
    client.close();
  }
}