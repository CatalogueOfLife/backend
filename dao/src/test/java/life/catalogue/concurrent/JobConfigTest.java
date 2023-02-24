package life.catalogue.concurrent;

import java.net.URI;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JobConfigTest {

  @Test
  public void downloadFile() {
    final JobConfig cfg = new JobConfig();

    UUID key = UUID.fromString("7ca06f44-2c0c-4fa9-a410-ac072c378378");
    assertEquals(URI.create("https://download.checklistbank.org/job/7c/" + key + ".zip"), cfg.downloadURI(key));

    cfg.downloadURI=URI.create("http://gbif.org/");
    assertEquals(URI.create("http://gbif.org/7c/" + key + ".zip"), cfg.downloadURI(key));

    cfg.downloadURI=URI.create("http://gbif.org/nonono/");
    assertEquals(URI.create("http://gbif.org/nonono/7c/" + key + ".zip"), cfg.downloadURI(key));
  }
}