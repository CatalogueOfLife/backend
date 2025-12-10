package life.catalogue.img;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThumborServiceTest {

  @Test
  public void test() {
    var cfg = new ThumborConfig();
    cfg.key = "0e9d6819-acea-c6fb-fb94-4edd592a5443-a3533e9d-2fb7-f759-450d-fefe27c9d8ee";
    var thumbor = new ThumborService(cfg);
    var url = URI.create("https://nh.kanagawa-museum.jp/kpmnh-collections/rest/media/L?cls=media_attin&pkey=753428&c3022");
    assertEquals("https%3A%2F%2Fnh.kanagawa-museum.jp%2Fkpmnh-collections%2Frest%2Fmedia%2FL%3Fcls%3Dmedia_attin%26pkey%3D753428%26c3022", ThumborService.urlEscape(url));
    System.out.println(thumbor.thumbnail(url));

    var enc = URLEncoder.encode(url.toString(), StandardCharsets.UTF_8);
    var url2 = URI.create(enc);
    System.out.println(thumbor.thumbnail(url2));
  }

}