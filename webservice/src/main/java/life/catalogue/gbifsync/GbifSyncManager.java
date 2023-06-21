package life.catalogue.gbifsync;

import life.catalogue.api.vocab.Users;
import life.catalogue.common.Managed;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;

import org.gbif.nameparser.utils.NamedThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
  private boolean started;
  private final GbifConfig cfg;
  private final DatasetDao ddao;
  private final SqlSessionFactory sessionFactory;
  private final Client client;
  private final List<ScheduledFuture<?>> futures = new ArrayList<>();

  public GbifSyncManager(GbifConfig gbif, DatasetDao ddao, SqlSessionFactory sessionFactory, Client client) {
    this.cfg = gbif;
    this.ddao = ddao;
    this.sessionFactory = sessionFactory;
    this.client = client;
  }

  @Override
  public boolean hasStarted() {
    return started;
  }
  
  public void syncNow() {
    Runnable job = new GbifSyncJob(cfg, client, ddao, sessionFactory, Users.GBIF_SYNC, true);
    job.run();
  }

  public Client getClient() {
    return client;
  }

  @Override
  public void start() throws Exception {
    if (cfg.syncFrequency > 0 || cfg.fullSyncFrequency > 0) {
      started = true;
      scheduler = Executors.newScheduledThreadPool(1,
          new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
      );

      if (cfg.fullSyncFrequency > 0) {
        LOG.info("Schedule a full GBIF registry sync incl deletions every {} days", cfg.fullSyncFrequency);
        futures.add(scheduler.scheduleAtFixedRate(
          new GbifSyncJob(cfg, client, ddao, sessionFactory, Users.GBIF_SYNC, false),
          0, cfg.fullSyncFrequency, TimeUnit.DAYS)
        );
      }

      if (cfg.syncFrequency > 0) {
        LOG.info("Enable incremental GBIF registry syncs every {} minutes", cfg.syncFrequency);
        // we delay the first run by 30 minutes as we do the full sync first
        futures.add(scheduler.scheduleAtFixedRate(
          new GbifSyncJob(cfg, client, ddao, sessionFactory, Users.GBIF_SYNC, true),
          30, cfg.syncFrequency, TimeUnit.MINUTES)
        );
      }

    } else {
      started = false;
      LOG.warn("Disable GBIF dataset sync");
    }
  }
  
  @Override
  public void stop() throws Exception {
    if (scheduler != null) {
      if (!futures.isEmpty()) {
        futures.forEach(f -> f.cancel(true));
      }
      ExecutorUtils.shutdown(scheduler, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
    }
    started = false;
  }
}
