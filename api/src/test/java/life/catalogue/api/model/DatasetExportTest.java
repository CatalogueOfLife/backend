package life.catalogue.api.model;

import java.net.URI;
import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasetExportTest {
  @Test
  public void downloadFile() {
    UUID key = UUID.fromString("7ca06f44-2c0c-4fa9-a410-ac072c378378");
    assertEquals(URI.create("https://download.catalogueoflife.org/exports/7c/" + key + ".zip"), DatasetExport.downloadURI(key));

    DatasetExport.setDownloadBaseURI(URI.create("http://gbif.org"));
    assertEquals(URI.create("http://gbif.org/7c/" + key + ".zip"), DatasetExport.downloadURI(key));

    DatasetExport.setDownloadBaseURI(URI.create("http://gbif.org/nonono"));
    assertEquals(URI.create("http://gbif.org/nonono/7c/" + key + ".zip"), DatasetExport.downloadURI(key));

    DatasetExport.setDownloadBaseURI(URI.create("http://gbif.org/nonono/"));
    assertEquals(URI.create("http://gbif.org/nonono/7c/" + key + ".zip"), DatasetExport.downloadURI(key));
  }
}