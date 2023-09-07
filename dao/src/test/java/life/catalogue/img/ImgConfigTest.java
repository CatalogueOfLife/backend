package life.catalogue.img;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class ImgConfigTest {

  @Test
  public void datasetlogoUrl() {
    var cfg = new ImgConfig();
    assertEquals(URI.create("https://api.checklistbank.org/dataset/3/logo"), cfg.datasetlogoUrl(3));
    assertEquals(URI.create("https://api.checklistbank.org/dataset/123/logo"), cfg.datasetlogoUrl(123));
  }
}