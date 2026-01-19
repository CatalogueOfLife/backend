package life.catalogue.api.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobResultTest {

  @Test
  void setDownloadConfigs() {
    final UUID key = UUID.fromString("c4c82948-15a9-4e95-b8e6-ac34047101ba");
    var job = new JobResult(key);
    final String zip = "c4/c4c82948-15a9-4e95-b8e6-ac34047101ba.zip";
    assertEquals(URI.create("https://download.checklistbank.org/job/"+zip), job.getDownload());

    JobResult.setDownloadConfigs(URI.create("https://download.checklistbank.org/job/"), new File("/mnt/auto/col/jobs"));
    assertEquals(URI.create("https://download.checklistbank.org/job/"+zip), job.getDownload());

    JobResult.setDownloadConfigs(URI.create("http://dw.gbif.org/jobs/"), new File("/mnt/auto/col/jobs"));
    assertEquals(URI.create("http://dw.gbif.org/jobs/"+zip), job.getDownload());
  }
}