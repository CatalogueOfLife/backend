package life.catalogue.cache;

import life.catalogue.api.event.DatasetChanged;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.http.impl.client.CloseableHttpClient;

import com.google.common.eventbus.Subscribe;


/**
 * Class to listen to dataset changes and invalidate the varnish cache if needed
 */
public class CacheFlush {
  private final UriBuilder projectUrlBuilder;
  private final UriBuilder datasetUrlBuilder;
  private final URI colseo;
  private final CloseableHttpClient client;

  public CacheFlush(CloseableHttpClient client, URI api) {
    this.client = client;
    this.projectUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}LR");
    this.datasetUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}/");
    this.colseo = UriBuilder.fromUri(api).path("colseo").build();
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      VarnishUtils.ban(client, datasetUrlBuilder.build(event.key));

    } else if (event.isUpdated()) {
      // did visibility of a releases change?
      if (event.obj.isPrivat() != event.old.isPrivat() && event.obj.getOrigin().isRelease()) {
        int projectKey = event.obj.getSourceKey();
        VarnishUtils.ban(client, projectUrlBuilder.build(projectKey));
        VarnishUtils.ban(client, colseo);
      }
    }
  }
}
