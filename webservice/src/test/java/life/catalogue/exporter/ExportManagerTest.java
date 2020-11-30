package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ExportManagerTest {

  @Test
  public void archiveURI() {
    WsServerConfig cfg = new WsServerConfig();
    cfg.downloadURI = URI.create("http://gbif.org");
    cfg.exportDir = new File("/tmp/col");
    ExportManager manager = new ExportManager(cfg, null, null);
    UUID key = UUID.fromString("7ca06f44-2c0c-4fa9-a410-ac072c378378");
    assertEquals(new File("/tmp/col/7c/7ca06f44-2c0c-4fa9-a410-ac072c378378.zip"), manager.archiveFiLe(key));
    assertEquals(URI.create("http://gbif.org/exports/7c/" + key + ".zip"), manager.archiveURI(key));
  }
}