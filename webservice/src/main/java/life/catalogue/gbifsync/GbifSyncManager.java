package life.catalogue.gbifsync;

import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.GbifConfig;

import org.gbif.nameparser.utils.NamedThreadFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.lifecycle.Managed;


/**
 * Syncs datasets from the GBIF registry
 */
public class GbifSyncManager implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSyncManager.class);
  private static final String THREAD_NAME = "gbif-sync";
  public static final UUID PLAZI_KEY = UUID.fromString("7ce8aef0-9e92-11dc-8738-b8a03c50a862");
  
  private ScheduledExecutorService scheduler;
  private GbifSyncJob job;
  private final GbifConfig cfg;
  private final SqlSessionFactory sessionFactory;
  private final Client client;
  
  public GbifSyncManager(GbifConfig gbif, SqlSessionFactory sessionFactory, Client client) {
    this.cfg = gbif;
    this.sessionFactory = sessionFactory;
    this.client = client;
  }

  public boolean hasStarted() {
    return job != null;
  }
  
  public void syncNow() {
    Runnable job = new GbifSyncJob(cfg, client, sessionFactory, Users.GBIF_SYNC);
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
      job = new GbifSyncJob(cfg, client, sessionFactory, Users.GBIF_SYNC);
      scheduler.scheduleAtFixedRate(job, 0, cfg.syncFrequency, TimeUnit.HOURS);
   
    } else {
      LOG.warn("Disable GBIF dataset sync");
    }
  }
  
  @Override
  public void stop() throws Exception {
    if (scheduler != null) {
      ExecutorUtils.shutdown(scheduler, ExecutorUtils.MILLIS_TO_DIE, TimeUnit.MILLISECONDS);
    }
    job = null;
  }
}
