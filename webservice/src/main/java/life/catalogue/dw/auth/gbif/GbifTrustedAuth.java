package life.catalogue.dw.auth.gbif;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import jakarta.ws.rs.core.HttpHeaders;

/**
 * Signs requests to the GBIF API as a GBIF trusted application acting as a proxied user,
 * following the scheme of gbif-common-ws 2.1.4 and later. GBIF rejects the signatures of older versions.
 * See https://github.com/gbif/gbif-common-ws#gbif-request-signing
 * <p>
 * The standard Authorization header carries the scheme, the application key and a signature of the request:
 * <pre>
 * Authorization: GBIF applicationKey:Signature
 * </pre>
 * The signature is the Base64 encoded HMAC-SHA256 with the application secret of these values joined by newlines:
 * <ol>
 *   <li>the http method</li>
 *   <li>the percent-encoded path with dot segments removed, followed by ? and the canonical query string if there is one</li>
 *   <li>the lower case content type, only if there is one</li>
 *   <li>the Content-MD5 header: Base64 of the MD5 of the body bytes, only if there is a body</li>
 *   <li>the x-gbif-user header with the proxied user</li>
 *   <li>the x-gbif-date header with the current time in RFC 1123, which GBIF only accepts within 15 minutes</li>
 * </ol>
 * With legacySigning configured requests are signed with the HMAC-SHA1 scheme of gbif-common-ws before 2.1.4 instead:
 * the decoded path, the lower case content type if there is one and the x-gbif-user, without date, query or body.
 * Only needed until all GBIF APIs we talk to are upgraded.
 */
public class GbifTrustedAuth {

  private static final Logger LOG = LoggerFactory.getLogger(GbifTrustedAuth.class);

  private static final String ALGORITHM = "HmacSHA256";
  private static final String GBIF_SCHEME = "GBIF";
  private static final String HEADER_GBIF_USER = "x-gbif-user";
  private static final String HEADER_GBIF_DATE = "x-gbif-date";
  private static final String HEADER_CONTENT_MD5 = "Content-MD5";
  private static final String HEADER_ORIGINAL_REQUEST_URL = "x-url";
  private static final char NEWLINE = '\n';

  private final String user;
  private final String appKey;
  private final String appSecret;
  private final Clock clock;
  private final boolean legacy;

  public GbifTrustedAuth(GBIFAuthenticationFactory cfg) {
    this(cfg, Clock.systemUTC());
  }

  @VisibleForTesting
  GbifTrustedAuth(GBIFAuthenticationFactory cfg, Clock clock) {
    this.user = Preconditions.checkNotNull(cfg.user, "A proxied GBIF user is required");
    this.appKey = Preconditions.checkNotNull(cfg.appkey, "To sign requests a GBIF app is required");
    this.appSecret = Preconditions.checkNotNull(cfg.secret, "To sign requests a GBIF secret for " + appKey + " is required");
    this.clock = clock;
    this.legacy = cfg.legacySigning;
    LOG.info("Use trusted GBIF appkey {} to proxy user {}{}", appKey, user, legacy ? " with legacy signing" : "");
  }

  /**
   * Signs an httpclient request by setting the x-gbif-user, x-gbif-date, x-url, Content-MD5 if there is a body,
   * and the Authorization header. Sign a request again for every attempt, so the date is fresh.
   *
   * @throws IllegalArgumentException if the request has a body that cannot be read twice
   */
  public void signRequest(HttpUriRequest request) {
    if (legacy) {
      signRequestLegacy(request);
      return;
    }
    final URI uri;
    try {
      uri = request.getUri();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid request URI " + request.getRequestUri(), e);
    }
    final String path = uri.normalize().getRawPath();
    request.setHeader(HEADER_GBIF_USER, user);
    request.setHeader(HEADER_GBIF_DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))));
    // the path as sent, GBIF accepts it behind its /v1 proxy prefix
    request.setHeader(HEADER_ORIGINAL_REQUEST_URL, path);
    request.removeHeaders(HEADER_CONTENT_MD5);

    String contentType = header(request, HttpHeaders.CONTENT_TYPE);
    HttpEntity entity = request.getEntity();
    if (entity != null) {
      // httpclient sends the content type of the entity unless the request has its own header
      if (contentType == null) {
        contentType = entity.getContentType();
      }
      byte[] body = body(entity);
      if (body.length > 0) {
        request.setHeader(HEADER_CONTENT_MD5, contentMd5(body));
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append(request.getMethod());
    sb.append(NEWLINE);
    sb.append(canonicalUrl(path, uri.getRawQuery()));
    if (contentType != null) {
      sb.append(NEWLINE);
      sb.append(contentType.toLowerCase());
    }
    String md5 = header(request, HEADER_CONTENT_MD5);
    if (md5 != null) {
      sb.append(NEWLINE);
      sb.append(md5);
    }
    sb.append(NEWLINE);
    sb.append(user);
    sb.append(NEWLINE);
    sb.append(header(request, HEADER_GBIF_DATE));
    final String stringToSign = sb.toString();
    LOG.debug("GBIF auth string to sign:\n{}", stringToSign);

    String header = GBIF_SCHEME + " " + appKey + ':' + hmac(ALGORITHM, stringToSign);
    LOG.debug("Adding authentication header to request {} for proxied user {} : {}", request.getRequestUri(), user, header);
    request.setHeader(HttpHeaders.AUTHORIZATION, header);
  }

  /**
   * The scheme of gbif-common-ws before 2.1.4. Remove once all GBIF APIs are upgraded.
   */
  private void signRequestLegacy(HttpUriRequest request) {
    String path;
    try {
      path = request.getUri().normalize().getPath();
    } catch (URISyntaxException e) {
      path = request.getRequestUri();
    }
    request.setHeader(HEADER_GBIF_USER, user);
    request.setHeader(HEADER_ORIGINAL_REQUEST_URL, path);

    StringBuilder sb = new StringBuilder();
    sb.append(request.getMethod());
    sb.append(NEWLINE);
    sb.append(path);
    String contentType = header(request, HttpHeaders.CONTENT_TYPE);
    if (contentType != null) {
      sb.append(NEWLINE);
      sb.append(contentType.toLowerCase());
    }
    sb.append(NEWLINE);
    sb.append(user);
    request.setHeader(HttpHeaders.AUTHORIZATION, GBIF_SCHEME + " " + appKey + ':' + hmac("HmacSHA1", sb.toString()));
  }

  private static String header(HttpUriRequest req, String name) {
    Header h = req.getFirstHeader(name);
    return h == null ? null : h.getValue();
  }

  private static byte[] body(HttpEntity entity) {
    if (!entity.isRepeatable()) {
      throw new IllegalArgumentException("Cannot sign a request with a body that can only be read once");
    }
    try {
      return EntityUtils.toByteArray(entity);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read the request body to sign it", e);
    }
  }

  @VisibleForTesting
  static String contentMd5(byte[] body) {
    try {
      return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(body));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot find MD5 message digester", e);
    }
  }

  /**
   * The path followed by the canonical query string, if any: parameters decoded, sorted by name and value
   * and encoded again as RFC 3986, an empty parameter dropped and one without value taken as an empty value.
   * Same as GbifAuthUtils.getCanonicalizedUrl of gbif-common-ws.
   */
  @VisibleForTesting
  static String canonicalUrl(String path, String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return path;
    }
    String query = Arrays.stream(rawQuery.split("&"))
      .filter(p -> !p.isEmpty())
      .map(p -> {
        int idx = p.indexOf('=');
        String name = idx < 0 ? p : p.substring(0, idx);
        String value = idx < 0 ? "" : p.substring(idx + 1);
        return new SimpleEntry<>(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
      })
      .sorted(Map.Entry.<String, String>comparingByKey().thenComparing(Map.Entry.comparingByValue()))
      .map(e -> encode(e.getKey()) + '=' + encode(e.getValue()))
      .collect(Collectors.joining("&"));
    return query.isEmpty() ? path : path + '?' + query;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
      .replace("+", "%20")
      .replace("*", "%2A")
      .replace("%7E", "~");
  }

  /**
   * Generates a Base64 encoded HMAC signature of the passed in string with the application secret.
   */
  private String hmac(String algorithm, String stringToSign) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), algorithm));
      return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot find " + algorithm + " message digester", e);
    } catch (InvalidKeyException e) {
      throw new RuntimeException("Invalid secret key for GBIF app " + appKey, e);
    }
  }

}
