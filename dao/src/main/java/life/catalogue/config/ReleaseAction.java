package life.catalogue.config;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.text.CitationUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Action to be called after a release with the URL being a templated allowed to contain
 * certain variables which will be replaced before the actual call:
 *  {DATASET_KEY}: the dataset key of the (x)release
 *  {ATTEMPT}: attempt number of the release in the project
 *  {VERSION}: version of the release
 *  {TITLE}: title of the release
 *  {ALIAS}: alias of the release
 *  {date}: date of today
 */
public class ReleaseAction {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseAction.class);

  public String method;
  public String url;

  /**
   * Call the action URI and return the http response code.
   * Any exceptions are converted into a return code -1
   * If the origin or private status does not match the action is not executed and zero is returned instead
   */
  public int call(CloseableHttpClient client, Dataset release) {
    URI uri = null;
    String x = null;
    try {
      x = CitationUtils.fromTemplate(escapedCopy(release), url);
      uri = new URI(x);
    } catch (IllegalArgumentException e) {
      LOG.warn("Bad URL template for action {} {}: {}", method, uri, e.getMessage());
      return -1;
    } catch (URISyntaxException e) {
      LOG.error("Failed to call release action with invalid URI: {}", x);
      return -1;
    }

    var req = ClassicRequestBuilder.create(method.trim().toUpperCase())
      .setUri(uri)
      .build();
    // execute
    LOG.info("{} {}", method, uri);
    try (CloseableHttpResponse response = client.execute(req)) {
      return response.getCode();
    } catch (Exception e) {
      LOG.error("Failed to {} {}: {}", method, uri, e.getMessage());
      return -1;
    }
  }

  /**
   * URL escapes alias,title and version to generate proper query params
   */
  private Dataset escapedCopy(Dataset release) {
    var copy = new Dataset(release);
    try {
      copy.setAlias(escape(release.getAlias()));
      copy.setTitle(escape(release.getTitle()));
      copy.setVersion(escape(release.getVersion()));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return copy;
  }

  private String escape(String x) throws UnsupportedEncodingException {
    return x == null ? x : URLEncoder.encode(x, StandardCharsets.UTF_8);
  }
}
