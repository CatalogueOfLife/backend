package life.catalogue.cache;

import com.google.common.eventbus.Subscribe;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import org.apache.http.impl.client.CloseableHttpClient;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;


/**
 * Class to listen to dataset changes and invalidate the cache if needed
 */
public class CacheFlush {
  private final UriBuilder projectUrlBuilder;
  private final UriBuilder datasetUrlBuilder;
  private final CloseableHttpClient client;

  public CacheFlush(CloseableHttpClient client, URI api) {
    this.client = client;
    this.projectUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}LR");
    this.datasetUrlBuilder = UriBuilder.fromUri(api).path("dataset/{key}/");
  }

  @Subscribe
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      VarnishUtils.ban(client, datasetUrlBuilder.build(event.key));

    } else if (event.isUpdated()) {
      // did visibility of a releases change?
      if (event.obj.isPrivat() != event.old.isPrivat() && event.obj.getOrigin() == DatasetOrigin.RELEASED) {
        int projectKey = event.obj.getSourceKey();
        VarnishUtils.ban(client, projectUrlBuilder.build(projectKey));
      }
    }
  }
}
