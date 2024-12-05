package life.catalogue.config;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.text.CitationUtils;

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
 *  {}date}: date of today
 */
public class ReleaseAction {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseAction.class);

  public String method;
  public String url;

  /**
   * If true the action will only be called when the release is being published
   */
  public boolean onPublish = false;

  /**
   * Filter for release origin, so the hook can only be applied to Releases or XReleases.
   * If none id given it applies to all.
   */
  public DatasetOrigin only;

  /**
   * Call the action URI and return the http response code.
   * Any exceptions are converted into a return code -1
   * If the origin or private status does not match the action is not executed and zero is returned instead
   */
  public int call(CloseableHttpClient client, Dataset release) {
    if (onPublish && release.isPrivat()) {
      LOG.info("Do not execute onPublish release action {} {}", method, url);
      return 0;
    }
    if (only != null && only != release.getOrigin()) {
      LOG.info("Do not execute {} only action {} {}", only, method, url);
      return 0;
    }

    String uri = null;
    try {
      uri = CitationUtils.fromTemplate(release, url);
    } catch (IllegalArgumentException e) {
      LOG.warn("Bad URL template for action {} {}: {}", method, uri, e.getMessage());
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
}
