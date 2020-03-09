package life.catalogue.release;

import life.catalogue.api.model.ColUser;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.common.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReleaseManager {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseManager.class);
  private static final ThreadPoolExecutor RELEASE_EXEC = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
      new ArrayBlockingQueue(1), new NamedThreadFactory("col-release"), new ThreadPoolExecutor.DiscardPolicy());

  private final AcExporter exporter;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;
  private final SqlSessionFactory factory;

  private CatalogueRelease release;

  public ReleaseManager(AcExporter exporter, DatasetImportDao diDao, NameUsageIndexService indexService, SqlSessionFactory factory) {
    this.exporter = exporter;
    this.diDao = diDao;
    this.indexService = indexService;
    this.factory = factory;
  }

  public Integer release(int catKey, ColUser user) {
    if (release != null) {
      throw new IllegalStateException("Release "+release.getSourceDatasetKey() + " to " + release.getReleaseKey() + " is already running");
    }

    release = CatalogueRelease.release(factory, indexService, exporter, diDao, catKey, user.getKey());
    final int key = release.getReleaseKey();

    CompletableFuture.runAsync(release, RELEASE_EXEC)
        .exceptionally(ex -> {
          LOG.error("Failed to release dataset {} into dataset {}", release.getSourceDatasetKey(), release.getReleaseKey(), ex);
          return null;
        })
        .thenApply(x -> {
          // clear release reference when job is done
          release = null;
          return x;
        });
    return key;
  }

  public Optional<DatasetImport> getReleaseMetrics() {
    return release == null ? Optional.empty() : Optional.of(release.getMetrics());
  }
}
