package life.catalogue.common.io;

import life.catalogue.common.date.DateUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 *
 */
public class DownloadUtil {
  private static final Logger LOG = LoggerFactory.getLogger(DownloadUtil.class);
  private static final String LAST_MODIFIED = "Last-Modified";
  private static final String MODIFIED_SINCE = "If-Modified-Since";
  private static final String AUTHORIZATION = "Authorization";
  private static final Pattern GITHUB_DOMAINS = Pattern.compile("github(usercontent)?\\.com", Pattern.CASE_INSENSITIVE);
  private static final Pattern GEOFF = Pattern.compile("^/gdower", Pattern.CASE_INSENSITIVE);
  //https://codeload.github.com/gdower/data-cycads/zip/master

  private final CloseableHttpClient hc;
  private final String githubToken;
  private final String githubTokenGeoff;
  
  public DownloadUtil(CloseableHttpClient hc) {
    this(hc, null, null);
  }
  
  public DownloadUtil(CloseableHttpClient hc, String githubToken, String githubTokenGeoff) {
    this.hc = hc;
    this.githubToken = githubToken;
    this.githubTokenGeoff = githubTokenGeoff;
  }
  
  /**
   * Downloads a uri to a local file, overwriting the file if it existed already.
   *
   * @param downloadTo file to download to
   * @throws DownloadException if any error occurred incl all http 4xx, 5xx responses
   */
  public void download(URI url, File downloadTo) throws DownloadException {
    downloadIfModifiedSince(url, null, downloadTo);
  }
  
  /**
   * Downloads a uri to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * since the last download.
   * <p>
   * Updates the last modified file property to reflect the last servers modified http header.
   *
   * @param downloadTo file to download to
   * @return true if changed and downloaded or false if unmodified since lastModified
   * @throws DownloadException if any error occurred incl all http 4xx, 5xx responses
   */
  public boolean downloadIfModified(URI url, File downloadTo, LocalDateTime lastModified) throws DownloadException {
    return downloadIfModifiedSince(url, lastModified, downloadTo);
  }

  /**
   * @return last modified timestamp of an existing local file or null if not existing
   */
  public static LocalDateTime lastModified(@Nullable File file) {
    LocalDateTime lastModified = null;
    if (file != null && file.exists()) {
      lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
    }
    return lastModified;
  }
  
  /**
   * Downloads a url to a file if its modified since the date given.
   * Updates the last modified file property to reflect the last servers modified http header.
   *
   * @param downloadTo   file to download to
   * @param lastModified last modified date to use in conditional get
   * @return true if changed or false if unmodified since lastModified
   * @throws DownloadException if any error occurred incl all http 4xx, 5xx responses
   */
  private boolean downloadIfModifiedSince(final URI url, final LocalDateTime lastModified, final File downloadTo) throws DownloadException {
    if (url == null) return false;
    if (url.getScheme().equalsIgnoreCase("ftp")) {
      return downloadIfModifiedSinceFTP(url, downloadTo);
    }
    
    HttpGet get = new HttpGet(url.toString());
    
    // prepare conditional GET request headers
    if (lastModified != null) {
      // DateTimeFormatter is threadsafe these days
      ZonedDateTime lastModifiedZoned = ZonedDateTime.of(lastModified, ZoneId.systemDefault());
      LOG.debug("Conditional GET: {}", DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModifiedZoned));
      get.addHeader(MODIFIED_SINCE, DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModifiedZoned));
    }
    
    if (githubToken != null && GITHUB_DOMAINS.matcher(url.getHost()).find()) {
      if (githubTokenGeoff != null && GEOFF.matcher(url.getPath()).find()) {
        LOG.debug("Adding Github API token from Geoff");
        get.addHeader(AUTHORIZATION, "token " + githubTokenGeoff);
      } else {
        LOG.debug("Adding Github API token");
        get.addHeader(AUTHORIZATION, "token " + githubToken);
      }
    }
    
    // execute, we do 2 manual redirect as HC avoids authenticated redirects
    try {
      return exec(get, url, downloadTo);
    } catch (RedirectException e) {
      var redirect = ClassicRequestBuilder.copy(get).setUri(e.location).build();
      try {
        return exec(redirect, e.location, downloadTo);
      } catch (RedirectException e2) {
        redirect = ClassicRequestBuilder.copy(get).setUri(e2.location).build();
        try {
          return exec(redirect, e2.location, downloadTo);
        } catch (RedirectException ex) {
          throw new DownloadException(e2.location, ex);
        }
      }
    }
  }

  private boolean exec(ClassicHttpRequest get, final URI url, final File downloadTo) throws DownloadException, RedirectException {
    try (CloseableHttpResponse response = hc.execute(get)) {
      final int status = response.getCode();
      System.out.println(String.format("%s -> %s", status, get));
      if (status == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");
        return false;

      } else if (status == HttpStatus.SC_MOVED_PERMANENTLY || status == HttpStatus.SC_MOVED_TEMPORARILY) {
        // handle redirects manually - HC avoids redirecting authenticated requests!
        final URI redirectUri = getRedirectLocation(url, get, response);
        throw new RedirectException(redirectUri);

      } else if (status / 100 == 2) {
        // write to file only when download succeeds
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
        return true;

      } else {
        LOG.warn("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), status);
        StringBuilder sb = new StringBuilder()
          .append("http ")
          .append(status)
          .append(" ")
          .append(response.getReasonPhrase())
          .append(" for URL ")
          .append(url);
        throw new DownloadException(url, sb.toString());
      }

    } catch (IOException e) {
      LOG.error("Failed to download {} to {}", url, downloadTo.getAbsolutePath(), e);
      throw new DownloadException(url, e);
    }
  }

  private static class RedirectException extends Exception {
    final URI location;

    private RedirectException(URI location) {
      this.location = location;
    }
  }

  private URI getRedirectLocation(final URI originalURL, final ClassicHttpRequest req, final CloseableHttpResponse resp) throws DownloadException {
    //get the location header to find out where to redirect to
    final Header locationHeader = resp.getFirstHeader(HttpHeaders.LOCATION);
    if (locationHeader == null) {
      throw new DownloadException(originalURL, "Redirect location is missing");
    }
    final String location = locationHeader.getValue();
    try {
      URI uri = createLocationURI(location);
      if (!uri.isAbsolute()) {
        // Resolve location URI
        uri = URIUtils.resolve(req.getUri(), uri);
      }
      return uri;

    } catch (final HttpException | URISyntaxException ex) {
      throw new DownloadException(originalURL, "Bad redirect location: " + location);
    }
  }

  private URI createLocationURI(final String location) throws ProtocolException {
    try {
      final URIBuilder b = new URIBuilder(new URI(location).normalize());
      final String host = b.getHost();
      if (host != null) {
        b.setHost(host.toLowerCase(Locale.ROOT));
      }
      if (b.isPathEmpty()) {
        b.setPathSegments("");
      }
      return b.build();
    } catch (final URISyntaxException ex) {
      throw new ProtocolException("Invalid redirect URI: " + location, ex);
    }
  }

  private boolean downloadIfModifiedSinceFTP(final URI url, final File downloadTo) throws DownloadException {
    LOG.debug("Use FTP for {}", url);
    try {
      // copy stream to local file
      downloadTo.getParentFile().mkdirs();
      try (InputStream stream = url.toURL().openStream();
           OutputStream fos = new FileOutputStream(downloadTo, false)
      ) {
        IOUtils.copy(stream, fos);
      }
    } catch (IOException e) {
      throw new DownloadException(url, e);
    }
    return true;
  }

  /**
   * Saves the entity of an http response to a local file, setting the local files
   * modified time to the last modified of the http response.
   * <p>
   * An existing file will be silently replaced.
   *
   * @param response
   * @param downloadTo
   * @throws IOException
   */
  private void saveToFile(CloseableHttpResponse response, File downloadTo) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      // copy stream to local file
      downloadTo.getParentFile().mkdirs();
      try (OutputStream fos = new FileOutputStream(downloadTo, false)) {
        entity.writeTo(fos);
      }
      // update last modified of file with http header date from server
      Header modHeader = response.getFirstHeader(LAST_MODIFIED);
      if (modHeader != null) {
        try {
          DateUtils.parseRFC1123(modHeader.getValue()).ifPresent(mod -> downloadTo.setLastModified(Instant.from(mod).toEpochMilli()));
        } catch (Exception e) {
          LOG.warn("Failed to set local file date to {} header {}", modHeader.getName(), modHeader.getValue(), e);
        }
      }
    }
  }

  public CloseableHttpClient getClient() {
    return hc;
  }
  
}
