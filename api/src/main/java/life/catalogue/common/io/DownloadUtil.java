package life.catalogue.common.io;

import life.catalogue.common.date.DateUtils;

import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
   * @return true if changed or false if unmodified since lastModified
   * @throws DownloadException if any error occurred incl all http 4xx, 5xx responses
   */
  public boolean downloadIfModified(URI url, File downloadTo) throws DownloadException {
    ZonedDateTime lastModified = null;
    if (downloadTo.exists()) {
      lastModified = ZonedDateTime.ofInstant(
          Instant.ofEpochMilli(downloadTo.lastModified()),
          ZoneId.systemDefault()
      );
    }
    return downloadIfModifiedSince(url, lastModified, downloadTo);
  }
  
  /**
   * @return last modified timestamp of the local download file
   * (which should be the same as the remote file)
   */
  public LocalDateTime lastModified(File file) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
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
  private boolean downloadIfModifiedSince(final URI url, final ZonedDateTime lastModified, final File downloadTo) throws DownloadException {
    if (url == null) return false;
    if (url.getScheme().equalsIgnoreCase("ftp")) {
      return downloadIfModifiedSinceFTP(url, lastModified, downloadTo);
    }
    
    HttpGet get = new HttpGet(url.toString());
    
    // prepare conditional GET request headers
    if (lastModified != null) {
      // DateTimeFormatter is threadsafe these days
      LOG.debug("Conditional GET: {}", DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModified));
      get.addHeader(MODIFIED_SINCE, DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModified));
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
    
    // execute
    try (CloseableHttpResponse response = hc.execute(get)) {
      final int status = response.getCode();
      System.out.println(String.format("%s -> %s", status, get));
      if (status == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");
        return false;
        
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
      throw new DownloadException(url, e);
    }
  }

  private boolean downloadIfModifiedSinceFTP(final URI url, final ZonedDateTime lastModified, final File downloadTo) throws DownloadException {
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
