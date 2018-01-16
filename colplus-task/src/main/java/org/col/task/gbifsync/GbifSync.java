package org.col.task.gbifsync;

import io.dropwizard.lifecycle.Managed;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.config.GbifConfig;
import org.col.db.mapper.DatasetMapper;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.gbif.utils.concurrent.ExecutorUtils;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.col.TaskServer.MDC_KEY_TASK;

/**
 * Syncs datasets from the GBIF registry
 */
public class GbifSync implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSync.class);
  private static final String THREAD_NAME = "gbif-sync";

  private ScheduledExecutorService scheduler;
  private final GbifSyncJob job;

  public GbifSync(GbifConfig gbif, SqlSessionFactory sessionFactory, RxClient<RxCompletionStageInvoker> rxClient) {
    this.job = new GbifSyncJob(gbif, rxClient, sessionFactory);
  }

  static class GbifSyncJob implements Runnable {
    private final RxClient<RxCompletionStageInvoker> rxClient;
    private final SqlSessionFactory sessionFactory;
    private final GbifConfig gbif;

    public GbifSyncJob(GbifConfig gbif, RxClient<RxCompletionStageInvoker> rxClient, SqlSessionFactory sessionFactory) {
      this.gbif = gbif;
      this.rxClient = rxClient;
      this.sessionFactory = sessionFactory;
    }

    @Override
    public void run() {
      MDC.put(MDC_KEY_TASK, getClass().getSimpleName());
      try (SqlSession session = sessionFactory.openSession(true)) {
        DatasetPager pager = new DatasetPager(rxClient, gbif);
        DatasetMapper mapper = session.getMapper(DatasetMapper.class);
        LOG.info("Syncing datasets from GBIF registry {}", gbif.api);
        sync(pager, mapper);
        session.commit();

      } catch (Exception e) {
        LOG.error("Failed to sync with GBIF", e);
      }
      MDC.remove(MDC_KEY_TASK);
    }

    private void sync(DatasetPager pager, DatasetMapper mapper) throws Exception {
      int created = 0;
      int updated = 0;
      int deleted = 0;

      while (pager.hasNext()) {
        List<Dataset> page = pager.next();
        LOG.debug("Received page " + pager.currPageNumber() + " with " + page.size() + " datasets from GBIF");

        for (Dataset gbif : page) {
          Dataset curr = mapper.getByGBIF(gbif.getGbifKey());
          if (curr == null) {
            // create new dataset
            mapper.create(gbif);
            created++;
            LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());

          } else
          /**
           * we modify core metadata (title, description, contacts, version) via the dwc archive metadata
           * gbif syncs only change one of the following
           *  - dwca access url
           *  - license
           *  - organization (publisher)
           *  - homepage
           */
            if (!gbif.getDataAccess().equals(curr.getDataAccess()) ||
                !gbif.getLicense().equals(curr.getLicense()) ||
                !gbif.getOrganisation().equals(curr.getOrganisation()) ||
                !gbif.getHomepage().equals(curr.getHomepage())
                ) {
              curr.setDataAccess(gbif.getDataAccess());
              curr.setLicense(gbif.getLicense());
              curr.setOrganisation(gbif.getOrganisation());
              curr.setHomepage(gbif.getHomepage());
              mapper.update(curr);
              updated++;
            }
        }
      }
      //TODO: delete datasets no longer in GBIF
      LOG.info("{} datasets added, {} updated, {} deleted", created, updated, deleted);
    }
  }

  @Override
  public void start() throws Exception {
    scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
    );
    LOG.info("Scheduling GBIF registry sync job every {} hours", job.gbif.syncFrequency);
    scheduler.scheduleAtFixedRate(job, 0, job.gbif.syncFrequency, TimeUnit.HOURS);
  }

  @Override
  public void stop() throws Exception {
    ExecutorUtils.stop(scheduler);
  }
}
