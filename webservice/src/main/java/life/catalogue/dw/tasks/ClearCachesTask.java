package life.catalogue.dw.tasks;

import life.catalogue.api.event.FlushCaches;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.auth.AuthBundle;
import life.catalogue.event.EventBroker;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import io.dropwizard.servlets.tasks.Task;

/**
 * Flushes the caches of this app. With broadcast=true it asks every app sharing the database to flush theirs,
 * e.g. after a deploy during which the old and new apps could not hear each other's events.
 */
public class ClearCachesTask extends Task {
  private final AuthBundle auth;
  private final LatestDatasetKeyCache ldk;
  private final EventBroker broker;

  public ClearCachesTask(AuthBundle auth, LatestDatasetKeyCache ldk, EventBroker broker) {
    super("flush-caches");
    this.auth = auth;
    this.ldk = ldk;
    this.broker = broker;
  }

  @Override
  public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
    if (parameters.getOrDefault("broadcast", List.of()).contains("true")) {
      broker.publish(new FlushCaches());
      output.println("Asked every app to flush its caches.");
      output.flush();
      return;
    }
    auth.flushCache();
    output.println("Flushed auth cache.");

    DatasetInfoCache.CACHE.clear();
    output.println("Flushed dataset info cache.");

    ldk.clear();
    output.println("Flushed latest dataset key cache.");
    output.flush();
  }
}
