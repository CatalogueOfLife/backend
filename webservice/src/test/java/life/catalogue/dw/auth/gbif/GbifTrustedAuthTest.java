package life.catalogue.dw.auth.gbif;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.Test;

import static org.junit.Assert.*;

public class GbifTrustedAuthTest {
  // Wed, 11 Nov 2026 15:04:05 GMT
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T15:04:05Z"), ZoneOffset.UTC);

  static GbifTrustedAuth auth() {
    var cfg = new GBIFAuthenticationFactory();
    cfg.appkey = "example.app";
    cfg.secret = "example-secret";
    cfg.user = "exampleuser";
    return new GbifTrustedAuth(cfg, CLOCK);
  }

  /**
   * The example of the gbif-common-ws README, https://github.com/gbif/gbif-common-ws#example
   */
  @Test
  public void readmeExample() throws Exception {
    var req = new HttpPost("https://api.gbif.org/v1/dataset?validate=true&tag=b*&tag=a+b");
    req.setEntity(new ByteArrayEntity("{\"title\":\"Example dataset\"}".getBytes(StandardCharsets.UTF_8), ContentType.create("application/json")));
    auth().signRequest(req);

    assertEquals("exampleuser", req.getFirstHeader("x-gbif-user").getValue());
    assertEquals("Wed, 11 Nov 2026 15:04:05 GMT", req.getFirstHeader("x-gbif-date").getValue());
    assertEquals("BW6JIbyk1iXd2gEjg2Lo9Q==", req.getFirstHeader("Content-MD5").getValue());
    assertEquals("GBIF example.app:dxGpb0dP4c87l4xhXGLRZFB3Rs+MZXYKAsPYWAkJyAE=", req.getFirstHeader("Authorization").getValue());
  }

  @Test
  public void get() throws Exception {
    var req = new HttpGet("https://api.gbif.org/v1/admin/user/some%20one");
    auth().signRequest(req);
    assertNull(req.getFirstHeader("Content-MD5"));
    assertEquals("/v1/admin/user/some%20one", req.getFirstHeader("x-url").getValue());
    // signing again replaces the headers instead of adding more
    auth().signRequest(req);
    assertEquals(1, req.getHeaders("Authorization").length);
    assertEquals(1, req.getHeaders("x-gbif-date").length);
  }

  /**
   * The scheme before gbif-common-ws 2.1.4: HMAC-SHA1 of method, decoded path and user
   */
  @Test
  public void legacy() throws Exception {
    var cfg = new GBIFAuthenticationFactory();
    cfg.appkey = "example.app";
    cfg.secret = "example-secret";
    cfg.user = "exampleuser";
    cfg.legacySigning = true;
    var req = new HttpGet("https://api.gbif.org/v1/admin/user/some%20one?x=1");
    new GbifTrustedAuth(cfg, CLOCK).signRequest(req);
    assertEquals("GBIF example.app:BuxZbvhCp0yy8dpyCnxc+Mi7cT4=", req.getFirstHeader("Authorization").getValue());
    assertEquals("exampleuser", req.getFirstHeader("x-gbif-user").getValue());
    assertEquals("/v1/admin/user/some one", req.getFirstHeader("x-url").getValue());
    assertNull(req.getFirstHeader("x-gbif-date"));
  }

  @Test
  public void canonicalUrl() throws Exception {
    assertEquals("/v1/dataset", GbifTrustedAuth.canonicalUrl("/v1/dataset", null));
    assertEquals("/v1/dataset", GbifTrustedAuth.canonicalUrl("/v1/dataset", "&"));
    assertEquals("/v1/dataset?tag=a%20b&tag=b%2A&validate=true", GbifTrustedAuth.canonicalUrl("/v1/dataset", "validate=true&tag=b*&tag=a+b"));
    assertEquals("/x?a=&b=~", GbifTrustedAuth.canonicalUrl("/x", "b=%7E&a"));
  }
}
