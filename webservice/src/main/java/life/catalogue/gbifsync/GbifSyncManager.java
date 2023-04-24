package life.catalogue.gbifsync;

import life.catalogue.api.vocab.Users;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;

import org.gbif.nameparser.utils.NamedThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Syncs datasets from the GBIF registry
 */
public class GbifSyncManager implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSyncManager.class);
  private static final String THREAD_NAME = "gbif-sync";

  private ScheduledExecutorService scheduler;
  private GbifSyncJob job;
  private final GbifConfig cfg;
  private final DatasetDao ddao;
  private final SqlSessionFactory sessionFactory;
  private final Client client;
  private ScheduledFuture<?> future;

  public GbifSyncManager(GbifConfig gbif, DatasetDao ddao, SqlSessionFactory sessionFactory, Client client) {
    this.cfg = gbif;
    this.ddao = ddao;
    this.sessionFactory = sessionFactory;
    this.client = client;
  }

  @Override
  public boolean hasStarted() {
    return job != null;
  }
  
  public void syncNow() {
    Runnable job = new GbifSyncJob(cfg, client, ddao, sessionFactory, Users.GBIF_SYNC);
    job.run();
  }

  public Client getClient() {
    return client;
  }

  @Override
  public void start() throws Exception {
    if (cfg.syncFrequency > 0) {
      scheduler = Executors.newScheduledThreadPool(1,
          new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
      );
      LOG.info("Enable GBIF registry sync job every {} hours", cfg.syncFrequency);
      job = new GbifSyncJob(cfg, client, ddao, sessionFactory, Users.GBIF_SYNC);
      future = scheduler.scheduleAtFixedRate(job, 1, cfg.syncFrequency, TimeUnit.HOURS);
   
    } else {
      LOG.warn("Disable GBIF dataset sync");
    }
  }
  
  @Override
  public void stop() throws Exception {
    if (scheduler != null) {
      if (future != null) {
        future.cancel(true);
      }
      ExecutorUtils.shutdown(scheduler, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
    }
    job = null;
  }
}
