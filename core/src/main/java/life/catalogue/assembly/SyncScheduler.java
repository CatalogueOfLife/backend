package life.catalogue.assembly;

import life.catalogue.api.model.Sector;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.AbstractPollingScheduler;
import life.catalogue.config.SyncManagerConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the projects with an enabled sync scheduler for outdated sectors and submits syncs for them.
 * Its own startable component rather than a thread owned by the SyncManager, which no longer has a
 * lifecycle of its own - it only validates, submits and cancels against the shared job executor.
 */
public class SyncScheduler extends AbstractPollingScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(SyncScheduler.class);
  private static final String THREAD_NAME = "sync-scheduler";
  private final SqlSessionFactory factory;
  private final SyncManager manager;
  private final SyncManagerConfig cfg;

  public SyncScheduler(SyncManagerConfig cfg, SyncManager manager, SqlSessionFactory factory) {
    super(THREAD_NAME, cfg.polling);
    this.manager = manager;
    this.factory = factory;
    this.cfg = cfg;
  }

  @Override
  protected void pollOnce() throws InterruptedException {
    while (isRunning() && !manager.isIdle()) {
      LOG.debug("Syncs busy, sleep for {} minutes", cfg.polling);
      TimeUnit.MINUTES.sleep(cfg.polling);
    }
    if (!isRunning()) {
      return;
    }
    List<Sector> sectors = fetch();
    if (sectors.isEmpty()) {
      LOG.debug("No sectors eligable to be synced. Sleep for {} minutes", cfg.polling);
      TimeUnit.MINUTES.sleep(cfg.polling);

    } else {
      LOG.info("Trying to schedule {} sector syncs", sectors.size());
      sectors.forEach(this::scheduleSync);
    }
  }

  private void scheduleSync(Sector s) {
    try {
      manager.sync(s, Users.IMPORTER);
    } catch (RuntimeException e) {
      // one sector that cannot be queued must not cost us the rest of the batch or the scheduler thread
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
