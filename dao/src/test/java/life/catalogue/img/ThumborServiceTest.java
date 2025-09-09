package life.catalogue.img;

import java.net.URI;

import org.junit.Test;

public class ThumborServiceTest {

  @Test
  public void test() {
    var cfg = new ThumborConfig();
    cfg.key = "44567fd-re32d3223d-23-32-e-23d-3-d-32dd3";
    var thumbor = new ThumborService(cfg);
    var url = URI.create("https://data.nhm.ac.uk/media/aea51bf9-e7c1-4c3d-a0d1-59ade253c111");
    System.out.println(thumbor.thumbnail(url));
  }

}