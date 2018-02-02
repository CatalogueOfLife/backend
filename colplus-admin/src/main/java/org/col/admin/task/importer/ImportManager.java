package org.col.admin.task.importer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import io.dropwizard.lifecycle.Managed;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.AdminServerConfig;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.db.mapper.DatasetMapper;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.gbif.utils.concurrent.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages import task scheduling, removing and listing
 */
public class ImportManager implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManager.class);
  public static final String THREAD_NAME = "dataset-importer";

  private ExecutorService exec;
  private final Map<Integer, Future> futures = Maps.newConcurrentMap();
  private final LinkedBlockingQueue<Runnable> queue;
  private final AdminServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;

  public class ImportRequest implements Callable<ImportJob> {
    public final int datasetKey;
    public final boolean force;
    public final LocalDateTime created = LocalDateTime.now();

    public ImportRequest(int datasetKey, boolean force) {
      this.datasetKey = datasetKey;
      this.force = force;
    }
    @Override
    public ImportJob call() {
      try (SqlSession session = factory.openSession(true)){
        Dataset d = session.getMapper(DatasetMapper.class).get(datasetKey);
        if (d == null) {
          throw new IllegalArgumentException("Dataset with key " + datasetKey + " does not exist");
        }
        return new ImportJob(d, force, cfg, downloader, factory);
      }
    }
  }

  public ImportManager(AdminServerConfig cfg, CloseableHttpClient client, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
    this.downloader = new DownloadUtil(client);
    queue = new LinkedBlockingQueue<>(cfg.importer.maxQueue);
  }

  /**
   * Lists the current queue
   */
  public List<ImportRequest> list() {
    return ImmutableList.copyOf(Iterators.filter(queue.iterator(), ImportRequest.class));
  }

  /**
   * Cancels a running import job by its dataset key
   */
  public void cancel(int datasetKey) {
    Future f = futures.remove(datasetKey);
    if (f != null) {
      LOG.info("Canceled import for dataset {}", datasetKey);
      f.cancel(true);

    } else {
      LOG.info("No import existing for dataset {}. Ignore", datasetKey);
    }
  }

  public ImportRequest submit(final int datasetKey, final boolean force) {
    LOG.info("Queue new import for dataset {}", datasetKey);
    ImportRequest req = new ImportRequest(datasetKey, force);
    futures.put(datasetKey, CompletableFuture
        .supplyAsync(() -> req, exec)
        .thenApply(ImportRequest::call)
        .whenComplete((job, err) -> {
          if (job != null) {
            DatasetImport di = job.call();
            Duration durQueued = Duration.between(req.created, di.getStarted());
            Duration durRun = Duration.between(di.getStarted(), LocalDateTime.now());
            LOG.info("Dataset import {}, attempt {} finished. {} min queued, {} min to execute", req.datasetKey, di.getAttempt(), durQueued.toMinutes(), durRun.toMinutes());

          } else {
            LOG.error("Dataset import {}, attempt {} failed.", req.datasetKey);
          }
          futures.remove(req.datasetKey);
        })
    );
    return req;
  }

  /**
   * @return true if queue is empty
   */
  public boolean isIdle() {
    return queue.isEmpty();
  }

  @Override
  public void start() throws Exception {
    exec = new ThreadPoolExecutor(0, cfg.importer.threads,
        10L, TimeUnit.SECONDS,
        queue,
        new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true),
        new ThreadPoolExecutor.AbortPolicy());
    ;
    // TODO: cleanup hanging imports in db
  }

  @Override
  public void stop() throws Exception {
    ExecutorUtils.stop(exec);
  }
}
