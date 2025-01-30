package life.catalogue.assembly;

import life.catalogue.api.model.Sector;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.config.SyncManagerConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SyncSchedulerJob implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(SyncSchedulerJob.class);
  private final SqlSessionFactory factory;
  private final SyncManager manager;
  private final SyncManagerConfig cfg;
  private volatile boolean running;

  public SyncSchedulerJob(SyncManagerConfig cfg, SyncManager manager, SqlSessionFactory factory) {
    this.manager = manager;
    this.factory = factory;
    this.cfg = cfg;
    this.running = true;
  }

  public void terminate() {
    running = false;
  }

  @Override
  public void run() {
    MDC.put(LoggingUtils.MDC_KEY_TASK, getClass().getSimpleName());

    while (running) {
      try {
        while (!manager.hasStarted()) {
          LOG.debug("Sync manager not started, sleep for {} minutes", cfg.polling);
          TimeUnit.MINUTES.sleep(cfg.polling);
        }
        while (!manager.isIdle()) {
          LOG.debug("Syncs busy, sleep for {} minutes", cfg.polling);
          TimeUnit.MINUTES.sleep(cfg.polling);
        }
        List<Sector> sectors = fetch();
        if (sectors.isEmpty()) {
          LOG.debug("No sectors eligable to be synced. Sleep for {} minutes", cfg.polling);
          TimeUnit.MINUTES.sleep(cfg.polling);

        } else {
          LOG.info("Trying to schedule {} sector syncs", sectors.size());
          sectors.forEach(this::scheduleSync);
        }
      } catch (InterruptedException e) {
        LOG.info("Interrupted sync scheduler. Stop");
        running = false;

      } catch (Exception e) {
        LOG.error("Error scheduling sectors. Shutdown sync scheduler!", e);
        running = false;
      }
    }
    MDC.remove(LoggingUtils.MDC_KEY_TASK);
  }

  private void scheduleSync(Sector s) {
    try {
      manager.sync(s, Users.IMPORTER);
    } catch (IllegalArgumentException e) {
      LOG.warn("Failed to schedule a sector sync {}", s, e);
    }
  }

  private List<Sector> fetch() {
    // go through projects and check outdated sectors if scheduler is active for the project
    try (SqlSession session = factory.openSession(true)) {
      var sectors = new ArrayList<Sector>();
      var req = new DatasetSearchRequest();
      req.setOrigin(List.of(DatasetOrigin.PROJECT));
      var dm = session.getMapper(DatasetMapper.class);
      var projectKeys = dm.searchKeys(req, Users.SUPERUSER);
      for (int projKey : projectKeys) {
        var settings = dm.getSettings(projKey);
        if (settings.isEnabled(Setting.SYNC_SCHEDULER)) {
          List<Integer> sourceKeys = settings.getList(Setting.SYNC_SCHEDULER_SOURCES);
          var outdated = session.getMapper(SectorMapper.class).listOutdatedSectors(projKey, sourceKeys);
          LOG.info("Scheduling {} outdated sector from project {}", outdated, projKey);
          sectors.addAll(outdated);
        }
      }
      return sectors;
    }
  }
}
