package life.catalogue.common.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Guards urls a client hands to the server to fetch, so the server cannot be used to reach itself or its internal network
 * (server side request forgery). Only http and https urls are allowed whose host resolves exclusively to public addresses:
 * loopback, wildcard, link local (incl. the cloud metadata address), private, unique local, carrier grade NAT, multicast and
 * other reserved ranges are refused. {@link #get(URI, int)} applies the check to every redirect it follows.
 * <p>
 * The http client resolves the host a second time when it connects, so a host whose DNS answer changes in between
 * (DNS rebinding) is not caught. See gbif/informatics#85 (A5, A8, B4).
 */
public class UrlPolicy {
  private static final Logger LOG = LoggerFactory.getLogger(UrlPolicy.class);
  private static final Set<String> SCHEMES = Set.of("http", "https");
  public static final int MAX_REDIRECTS = 3;
  public static final int DEFAULT_MAX_BYTES = 10 * 1024 * 1024;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @FunctionalInterface
  public interface Resolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  public static final UrlPolicy PUBLIC = new UrlPolicy(InetAddress::getAllByName);

  private final Resolver resolver;
  private volatile HttpClient client;

  @VisibleForTesting
  UrlPolicy(Resolver resolver) {
    this.resolver = resolver;
  }

  /**
   * @throws IllegalArgumentException if the url is not a http(s) url of a public host
   */
  public void check(URI url) throws IllegalArgumentException {
    if (url == null) {
      throw new IllegalArgumentException("URL required");
    }
    if (url.getScheme() == null || !SCHEMES.contains(url.getScheme().toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Only http and https URLs are supported");
    }
    String host = url.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("URL without a host");
    }
    // strip the brackets of IPv6 literals
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Unknown host " + host);
    }
    if (addresses == null || addresses.length == 0) {
      throw new IllegalArgumentException("Unknown host " + host);
    }
    for (InetAddress a : addresses) {
      if (!isPublic(a)) {
        LOG.info("Refused URL {} resolving to non public address {}", url, a.getHostAddress());
        throw new IllegalArgumentException("URL host " + host + " is not public");
      }
    }
  }

  /**
   * @return true if the address is routable on the public internet
   */
  @VisibleForTesting
  static boolean isPublic(InetAddress a) {
    if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
      || a.isMulticastAddress()) {
      return false;
    }
    byte[] b = a.getAddress();
    if (a instanceof Inet4Address) {
      return isPublicV4(b);
    }
    if (a instanceof Inet6Address) {
      // unique local fc00::/7
      if ((b[0] & 0xFE) == 0xFC) return false;
      // IPv4 mapped ::ffff:0:0/96, IPv4 compatible ::/96 and NAT64 64:ff9b::/96 embed an IPv4 address
      boolean first10Zero = true;
      for (int i = 0; i < 10; i++) {
        if (b[i] != 0) first10Zero = false;
      }
      boolean mapped = first10Zero && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
      boolean compat = first10Zero && b[10] == 0 && b[11] == 0;
      boolean nat64 = (b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B;
      if (mapped || compat || nat64) {
        return isPublicV4(new byte[]{b[12], b[13], b[14], b[15]});
      }
      // documentation 2001:db8::/32
      if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8) return false;
      return true;
    }
    return false;
  }

  private static boolean isPublicV4(byte[] b) {
    int b0 = b[0] & 0xFF;
    int b1 = b[1] & 0xFF;
    int b2 = b[2] & 0xFF;
    return !(b0 == 0                                  // 0.0.0.0/8 this network
      || b0 == 10                                     // 10/8 private
      || b0 == 127                                    // loopback
      || (b0 == 100 && b1 >= 64 && b1 <= 127)         // 100.64/10 carrier grade NAT
      || (b0 == 169 && b1 == 254)                     // link local incl. cloud metadata
      || (b0 == 172 && b1 >= 16 && b1 <= 31)          // 172.16/12 private
      || (b0 == 192 && b1 == 0 && b2 == 0)            // 192.0.0/24 protocol assignments
      || (b0 == 192 && b1 == 168)                     // 192.168/16 private
      || (b0 == 198 && (b1 == 18 || b1 == 19))        // 198.18/15 benchmarking
      || b0 >= 224                                    // multicast and reserved, incl. broadcast
    );
  }

  /**
   * GETs a public url, following at most {@link #MAX_REDIRECTS} redirects, each of which is checked again.
   *
   * @param maxBytes the largest body accepted
   * @return the body of a 2xx response
   * @throws IllegalArgumentException if the url or a redirect is not allowed
   * @throws IOException if the request fails, does not answer 2xx or the body is too large
   */
  public byte[] get(URI url, int maxBytes) throws IOException, InterruptedException {
    URI current = url;
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      check(current);
      var req = HttpRequest.newBuilder(current)
        .timeout(TIMEOUT)
        .header("User-Agent", "ChecklistBank/1.0")
        .GET()
        .build();
      var resp = client().send(req, HttpResponse.BodyHandlers.ofInputStream());
      int status = resp.statusCode();
      if (status / 100 == 3) {
        try (InputStream ignored = resp.body()) {
          var location = resp.headers().firstValue("Location")
            .orElseThrow(() -> new IOException("Redirect without location from " + url));
          current = current.resolve(location);
        }
        continue;
      }
      try (InputStream in = resp.body()) {
        if (status / 100 != 2) {
          throw new IOException("GET request " + current + " failed with http " + status);
        }
        return readLimited(in, maxBytes);
      }
    }
    throw new IOException("Too many redirects for " + url);
  }

  private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
    var out = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      if (out.size() + n > maxBytes) {
        throw new IOException("Response exceeds the maximum size of " + maxBytes + " bytes");
      }
      out.write(buf, 0, n);
    }
    return out.toByteArray();
  }

  private HttpClient client() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          // redirects are followed manually, so each target is checked
          client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();
        }
      }
    }
    return client;
  }
}
