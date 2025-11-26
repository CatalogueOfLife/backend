package life.catalogue.img;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ThumborServiceTest {

  @Test
  public void test() {
    var cfg = new ThumborConfig();
    cfg.key = "0e9d6819-acea-c6fb-fb94-4edd592a5443-a3533e9d-2fb7-f759-450d-fefe27c9d8ee";
    var thumbor = new ThumborService(cfg);
    var url = URI.create("https://data.nhm.ac.uk/media/aea51bf9-e7c1-4c3d-a0d1-59ade253c111");
    System.out.println(thumbor.thumbnail(url));

    var enc = URLEncoder.encode(url.toString(), StandardCharsets.UTF_8);
    var url2 = URI.create(enc);
    System.out.println(thumbor.thumbnail(url2));
  }

}