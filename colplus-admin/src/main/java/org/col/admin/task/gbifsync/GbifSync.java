package org.col.admin.task.gbifsync;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.dropwizard.lifecycle.Managed;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.AdminServer;
import org.col.admin.config.GbifConfig;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.common.concurrent.ExecutorUtils;
import org.col.db.mapper.DatasetMapper;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.glassfish.jersey.client.rx.RxClient;
import org.glassfish.jersey.client.rx.java8.RxCompletionStageInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.col.admin.AdminServer.MILLIS_TO_DIE;

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
    private int created;
    private int updated;
    private int deleted;
    private DatasetMapper mapper;
    private DatasetPager pager;

    public GbifSyncJob(GbifConfig gbif, RxClient<RxCompletionStageInvoker> rxClient, SqlSessionFactory sessionFactory) {
      this.gbif = gbif;
      this.rxClient = rxClient;
      this.sessionFactory = sessionFactory;
    }

    @Override
    public void run() {
      MDC.put(AdminServer.MDC_KEY_TASK, getClass().getSimpleName());
      try (SqlSession session = sessionFactory.openSession(true)) {
        pager = new DatasetPager(rxClient, gbif);
        mapper = session.getMapper(DatasetMapper.class);
        if (gbif.insert) {
          syncAll();
        } else {
          updateExisting();
        }
        session.commit();
        LOG.info("{} datasets added, {} updated, {} deleted", created, updated, deleted);

      } catch (Exception e) {
        LOG.error("Failed to sync with GBIF", e);
      }
      MDC.remove(AdminServer.MDC_KEY_TASK);
    }


    private void syncAll() throws Exception {
      LOG.info("Syncing all datasets from GBIF registry {}", gbif.api);
      while (pager.hasNext()) {
        List<Dataset> page = pager.next();
        LOG.debug("Received page " + pager.currPageNumber() + " with " + page.size() + " datasets from GBIF");
        for (Dataset gbif : page) {
          sync(gbif, mapper.getByGBIF(gbif.getGbifKey()));
        }
      }
      //TODO: delete datasets no longer in GBIF
    }

    private void updateExisting() throws Exception {
      LOG.info("Syncing existing datasets with GBIF registry {}", gbif.api);
      Page page = new Page(100);
      List<Dataset> datasets = null;
      while (datasets == null || !datasets.isEmpty()) {
        datasets = mapper.list(page);
        for (Dataset d : datasets) {
          if (d.getGbifKey() != null) {
            Dataset gbif = pager.get(d.getGbifKey());
            sync(gbif, d);
          }
        }
        page.next();
      }
    }

    private void sync(Dataset gbif, Dataset curr) throws Exception {
      if (curr == null) {
        // create new dataset
        mapper.create(gbif);
        created++;
        LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());

      } else if (!Objects.equals(gbif.getDataAccess(), curr.getDataAccess()) ||
            !Objects.equals(gbif.getLicense(), curr.getLicense()) ||
            !Objects.equals(gbif.getOrganisation(), curr.getOrganisation()) ||
            !Objects.equals(gbif.getHomepage(), curr.getHomepage())
      ) {
        //we modify core metadata (title, description, contacts, version) via the dwc archive metadata
        //gbif syncs only change one of the following
        // - dwca access url
        // - license
        // - organization (publisher)
        // - homepage
        curr.setDataAccess(gbif.getDataAccess());
        curr.setLicense(gbif.getLicense());
        curr.setOrganisation(gbif.getOrganisation());
        curr.setHomepage(gbif.getHomepage());
        mapper.update(curr);
        updated++;
      }
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
    ExecutorUtils.shutdown(scheduler, MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
  }
}
