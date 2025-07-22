package life.catalogue.dw.tasks;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.auth.AuthBundle;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import io.dropwizard.servlets.tasks.Task;

public class ClearCachesTask extends Task {
  private final AuthBundle auth;
  private final LatestDatasetKeyCache ldk;

  public ClearCachesTask(AuthBundle auth, LatestDatasetKeyCache ldk) {
    super("flush-caches");
    this.auth = auth;
    this.ldk = ldk;
  }

  @Override
  public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
    auth.flushCache();
    output.println("Flushed auth cache.");

    DatasetInfoCache.CACHE.clear();
    output.println("Flushed dataset info cache.");

    ldk.clear();
    output.println("Flushed latest dataset key cache.");
    output.flush();
  }
}