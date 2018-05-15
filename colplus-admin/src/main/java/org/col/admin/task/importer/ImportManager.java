package org.col.admin.task.importer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.dropwizard.lifecycle.Managed;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.AdminServerConfig;
import org.col.api.model.CslData;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.util.PagingUtil;
import org.col.api.vocab.ImportState;
import org.col.common.io.DownloadUtil;
import org.col.db.dao.DatasetImportDao;
import org.col.db.mapper.DatasetMapper;
import org.col.parser.Parser;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.gbif.utils.concurrent.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages import task scheduling, removing and listing
 */
public class ImportManager implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManager.class);
  public static final String THREAD_NAME = "dataset-importer";

  private ExecutorService exec;
  private final List<ImportRequest> queue = Lists.newArrayList();
  private final Map<Integer, Future> futures = Maps.newConcurrentMap();
  private final AdminServerConfig cfg;
  private final DownloadUtil downloader;
  private final SqlSessionFactory factory;
  private final Parser<CslData> cslParser;

  public ImportManager(AdminServerConfig cfg, CloseableHttpClient client, SqlSessionFactory factory, Parser<CslData> cslParser) {
    this.cfg = cfg;
    this.factory = factory;
    this.downloader = new DownloadUtil(client);
    this.cslParser = cslParser;
  }

  /**
   * Lists the current queue
   */
  public List<ImportRequest> list() {
    return queue;
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
    final ImportRequest req = new ImportRequest(datasetKey, force);
    queue.add(req);
    futures.put(datasetKey, CompletableFuture
        .supplyAsync(() -> req)
        .thenApplyAsync(this::runImport, exec)
        .whenComplete((di, err) -> {
          if (err != null) {
            LOG.error("Dataset import {} failed: {}", req.datasetKey, err);

          } else {
            Duration durQueued = Duration.between(req.created, di.getStarted());
            Duration durRun = Duration.between(di.getStarted(), LocalDateTime.now());
            LOG.info("Dataset import {}, attempt {} finished. {} min queued, {} min to execute", req.datasetKey, di.getAttempt(), durQueued.toMinutes(), durRun.toMinutes());
          }
          futures.remove(req.datasetKey);
          queue.remove(req);
        })
    );
    LOG.info("Queued import for dataset {}", datasetKey);
    return req;
  }

  private DatasetImport runImport(ImportRequest req) {
    try (SqlSession session = factory.openSession(true)) {
      Dataset d = session.getMapper(DatasetMapper.class).get(req.datasetKey);
      if (d == null) {
        throw new IllegalArgumentException("Dataset " + req.datasetKey + " does not exist");

      } else if (d.hasDeletedDate()) {
        throw new IllegalArgumentException("Dataset " + req.datasetKey + " is deleted");
      }
      ImportJob job = new ImportJob(d, req.force, cfg, downloader, factory, cslParser);
      req.start();
      return job.call();
    }
  }

  /**
   * @return true if queue is empty
   */
  public boolean isIdle() {
    return queue.isEmpty();
  }

  @Override
  public void start() throws Exception {
    LOG.info("Starting import manager with {} import threads.", cfg.importer.threads);
    exec = new ThreadPoolExecutor(0, cfg.importer.threads,
        10L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(cfg.importer.maxQueue),
        new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true),
        new ThreadPoolExecutor.AbortPolicy()
    );
    // read hanging imports in db and add as new requests to the queue
    int counter = 0;
    DatasetImportDao dao = new DatasetImportDao(factory);
    Iterator<DatasetImport> iter = PagingUtil.pageAll(p -> dao.list(ImportState.RUNNING, p));
    while (iter.hasNext()) {
      DatasetImport di = iter.next();
      di.setState(ImportState.CANCELED);
      dao.update(di);
      // add back to queue
      submit(di.getDatasetKey(), true);
      counter++;
    }
    LOG.info("Canceled and resubmitted all {} dangling imports.", counter);
  }

  @Override
  public void stop() throws Exception {
    ExecutorUtils.stop(exec);
  }
}
