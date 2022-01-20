package life.catalogue.gbifsync;

import io.dropwizard.lifecycle.Managed;
import it.unimi.dsi.fastutil.ints.Int2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.config.GbifConfig;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.utils.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.ws.rs.client.Client;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Syncs datasets from the GBIF registry
 */
public class GbifSync implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSync.class);
  private static final String THREAD_NAME = "gbif-sync";
  public static final UUID PLAZI_KEY = UUID.fromString("7ce8aef0-9e92-11dc-8738-b8a03c50a862");
  
  private ScheduledExecutorService scheduler;
  private GbifSyncJob job;
  private final GbifConfig cfg;
  private final SqlSessionFactory sessionFactory;
  private final Client client;
  
  public GbifSync(GbifConfig gbif, SqlSessionFactory sessionFactory, Client client) {
    this.cfg = gbif;
    this.sessionFactory = sessionFactory;
    this.client = client;
  }
  
  static class GbifSyncJob implements Runnable {
    private final Client client;
    private final SqlSessionFactory sessionFactory;
    private final GbifConfig gbif;
    private int created;
    private int updated;
    private int deleted;
    private DatasetMapper mapper;
    private DatasetPager pager;
    
    public GbifSyncJob(GbifConfig gbif, Client client, SqlSessionFactory sessionFactory) {
      this.gbif = gbif;
      this.client = client;
      this.sessionFactory = sessionFactory;
    }
    
    @Override
    public void run() {
      MDC.put(LoggingUtils.MDC_KEY_TASK, getClass().getSimpleName());
      try (SqlSession session = sessionFactory.openSession(true)) {
        pager = new DatasetPager(client, gbif);
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
      MDC.remove(LoggingUtils.MDC_KEY_TASK);
    }
    
    
    private void syncAll() throws Exception {
      final IntSet keys = new IntOpenHashSet();
      LOG.info("Syncing all datasets from GBIF registry {}", gbif.api);
      while (pager.hasNext()) {
        List<DatasetWithSettings> page = pager.next();
        LOG.debug("Received page " + pager.currPageNumber() + " with " + page.size() + " datasets from GBIF");
        for (DatasetWithSettings gbif : page) {
          Dataset d = mapper.getByGBIF(gbif.getGbifKey());
          DatasetWithSettings curr = null;
          if (d != null) {
            DatasetSettings s = mapper.getSettings(d.getKey());
            curr = new DatasetWithSettings(d, s);
          }
          sync(gbif, curr);
          keys.add(gbif.getKey());
        }
      }
      // report datasets no longer in GBIF
      mapper.listGBIF().forEach(key -> {
        if (!keys.contains(key)) {
          // this key was not seen in this registry sync round before - TODO: delete it ???
          Dataset d = mapper.get(key);
          LOG.warn("Dataset {} {} missing in GBIF but has key {}", key, d.getTitle(), d.getGbifKey());
        }
      });
    }
    
    private void updateExisting() throws Exception {
      LOG.info("Syncing existing datasets with GBIF registry {}", gbif.api);
      Page page = new Page(100);
      List<Dataset> datasets = null;
      while (datasets == null || !datasets.isEmpty()) {
        datasets = mapper.list(page);
        for (Dataset d : datasets) {
          if (d.getGbifKey() != null) {
            DatasetWithSettings gbif = pager.get(d.getGbifKey());
            DatasetWithSettings curr = new DatasetWithSettings(d, mapper.getSettings(d.getKey()));
            sync(gbif, curr);
          }
        }
        page.next();
      }
    }
    
    private void sync(DatasetWithSettings gbif, DatasetWithSettings curr) {
      try {
        if (curr == null) {
          // create new dataset
          gbif.setCreatedBy(Users.GBIF_SYNC);
          gbif.setModifiedBy(Users.GBIF_SYNC);
          mapper.create(gbif.getDataset());
          mapper.updateSettings(gbif.getKey(), gbif.getSettings(), Users.GBIF_SYNC);
          created++;
          LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());

        } else if (curr.has(Setting.GBIF_SYNC_LOCK) && curr.getBool(Setting.GBIF_SYNC_LOCK)) {
          LOG.info("Dataset {} is locked for GBIF updates: {}", gbif.getKey(), gbif.getTitle());

        } else if (!Objects.equals(gbif.getDataAccess(), curr.getDataAccess()) ||
                   !Objects.equals(gbif.getLicense(), curr.getLicense()) ||
                   !Objects.equals(gbif.getPublisher(), curr.getPublisher()) ||
                   !Objects.equals(gbif.getUrl(), curr.getUrl()) ||
                   !Objects.equals(gbif.getDoi(), curr.getDoi())
        ) {
          // we modify core metadata (title, description, contacts, version) via the dwc archive metadata
          //gbif syncs only change one of the following
          // - dwca access url
          // - license
          // - publisher (publishOrgKey)
          // - homepage
          // - doi
          curr.setDataAccess(gbif.getDataAccess());
          curr.setLicense(gbif.getLicense());
          curr.setPublisher(gbif.getPublisher());
          curr.setUrl(gbif.getUrl());
          curr.setDoi(gbif.getDoi());
          mapper.updateAll(curr);
          updated++;
        }
      } catch (Exception e) {
        LOG.error("Failed to sync GBIF dataset {} >{}<", gbif.getGbifKey(), gbif.getTitle(), e);
      }
    }
  }
  
  public boolean hasStarted() {
    return job != null;
  }
  
  public void syncNow() {
    Runnable job = new GbifSyncJob(cfg, client, sessionFactory);
    job.run();
  }
  
  @Override
  public void start() throws Exception {
    if (cfg.syncFrequency > 0) {
      scheduler = Executors.newScheduledThreadPool(1,
          new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true)
      );
      LOG.info("Enable GBIF registry sync job every {} hours", cfg.syncFrequency);
      job = new GbifSyncJob(cfg, client, sessionFactory);
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
