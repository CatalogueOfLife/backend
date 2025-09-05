package life.catalogue.cache;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DatasetLogoChanged;

import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import jakarta.ws.rs.core.UriBuilder;


/**
 * Class to listen to dataset changes and invalidate the varnish cache if needed
 */
public class CacheFlush implements DatasetListener {
  private final UriBuilder projectUrlBuilder;
  private final UriBuilder datasetUrlBuilder;
  private final UriBuilder logoUrlBuilder;
  private final URI dataset;
  private final URI colseo;
  private final CloseableHttpClient client;

  public CacheFlush(CloseableHttpClient client, URI api) {
    this.client = client;
    this.projectUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}LR");
    this.datasetUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}/");
    this.logoUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}/logo");
    this.colseo = UriBuilder.fromUri(api).path("colseo").build();
    this.dataset= UriBuilder.fromUri(api).path("dataset").build();
  }

  @Override
  public void datasetDataChanged(DatasetDataChanged event){
    if (event.datasetKey < 0) {
      VarnishUtils.ban(client, dataset);
    } else {
      VarnishUtils.ban(client, datasetUrlBuilder.build(event.datasetKey));
    }
  }

  @Override
  public void datasetLogoChanged(DatasetLogoChanged event){
    VarnishUtils.ban(client, logoUrlBuilder.build(event.datasetKey));
  }

  @Override
  public void datasetChanged(DatasetChanged event){
    if (!event.isCreated()) {
      VarnishUtils.ban(client, datasetUrlBuilder.build(event.key));
    }
    // did visibility of a releases change? ban project URLs
    if (event.isUpdated() && event.obj.isPrivat() != event.old.isPrivat() && event.obj.getOrigin().isRelease()) {
      int projectKey = event.obj.getSourceKey();
      VarnishUtils.ban(client, projectUrlBuilder.build(projectKey));
      VarnishUtils.ban(client, colseo);
    }
  }
}
