package life.catalogue.img;

import java.net.URI;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThumborServiceTest {

  @Test
  public void test() {
    var cfg = new ThumborConfig();
    cfg.key = "44567fd-re32d3223d-23-32-e-23d-3-d-32dd3";
    var thumbor = new ThumborService(cfg);
    var url = URI.create("https://nh.kanagawa-museum.jp/kpmnh-collections/rest/media/L?cls=media_attin&pkey=753428&c3022");
    assertEquals("https%3A%2F%2Fnh.kanagawa-museum.jp%2Fkpmnh-collections%2Frest%2Fmedia%2FL%3Fcls%3Dmedia_attin%26pkey%3D753428%26c3022", ThumborService.urlEscape(url));
    System.out.println(thumbor.thumbnail(url));
  }

}