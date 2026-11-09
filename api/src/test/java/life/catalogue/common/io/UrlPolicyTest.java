package life.catalogue.common.io;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.junit.Test;

import static org.junit.Assert.*;

public class UrlPolicyTest {

  /**
   * Resolves literals as usual, a few fixed names without any network, and nothing else.
   */
  private static final UrlPolicy POLICY = new UrlPolicy(host -> switch (host) {
    case "example.org" -> new InetAddress[]{InetAddress.getByName("93.184.215.14")};
    case "internal.example.org" -> new InetAddress[]{InetAddress.getByName("93.184.215.14"), InetAddress.getByName("10.1.2.3")};
    case "localhost" -> new InetAddress[]{InetAddress.getByName("127.0.0.1")};
    default -> {
      if (host.matches("^[0-9.]+$") || host.contains(":")) {
        yield InetAddress.getAllByName(host);
      }
      throw new UnknownHostException(host);
    }
  });

  @Test
  public void allowed() {
    POLICY.check(URI.create("https://example.org/metadata.yaml"));
    POLICY.check(URI.create("http://example.org:8080/eml.xml"));
    POLICY.check(URI.create("http://93.184.215.14/x"));
    POLICY.check(URI.create("http://[2a00:1450:4001:80b::200e]/x"));
  }

  @Test
  public void refused() {
    for (String url : new String[]{
      "file:///etc/passwd",
      "jar:file:///tmp/x.jar!/a",
      "ftp://example.org/x",
      "gopher://example.org/x",
      "/relative/path",
      "http:///nohost",
      "http://localhost:8081/tasks/gc",
      "http://127.0.0.1/",
      "http://127.1.2.3/",
      "http://0.0.0.0/",
      "http://[::1]/",
      "http://[::]/",
      "http://[::ffff:127.0.0.1]/",
      "http://[::ffff:10.0.0.1]/",
      "http://[64:ff9b::a9fe:a9fe]/",
      "http://[fd00::1]/",
      "http://[fe80::1]/",
      "http://169.254.169.254/latest/meta-data/",
      "http://10.0.0.1/",
      "http://172.16.0.1/",
      "http://172.31.255.255/",
      "http://192.168.1.1/",
      "http://100.64.0.1/",
      "http://224.0.0.1/",
      "http://255.255.255.255/",
      "http://internal.example.org/",
      "http://unknown.invalid/"
    }) {
      try {
        POLICY.check(URI.create(url));
        fail("URL allowed: " + url);
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void nullUrl() {
    assertThrows(IllegalArgumentException.class, () -> POLICY.check(null));
  }
}
