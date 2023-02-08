package life.catalogue.gbifsync;

import life.catalogue.api.exception.NotUniqueException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.*;

import javax.ws.rs.client.Client;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class GbifSyncJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSyncJob.class);
  private final Client client;
  private final SqlSessionFactory sessionFactory;
  private final DatasetDao dao;
  private final GbifConfig cfg;
  private Set<UUID> keys;
  private int created;
  private int updated;
  private int deleted;
  private DatasetPager pager;

  public GbifSyncJob(GbifConfig cfg, Client client, DatasetDao ddao, SqlSessionFactory sessionFactory, int userKey) {
    this(cfg, client, ddao, sessionFactory, userKey, Collections.emptySet());
  }

  public GbifSyncJob(GbifConfig cfg, Client client, DatasetDao ddao, SqlSessionFactory sessionFactory, int userKey, Set<UUID> keys) {
    super(userKey, JobPriority.HIGH);
    this.cfg = cfg;
    this.client = client;
    this.dao = ddao;
    this.sessionFactory = sessionFactory;
    this.keys = keys == null ? new HashSet<>() : keys;
  }

  @Override
  public void execute() throws Exception {
    pager = new DatasetPager(client, cfg);
    if (!keys.isEmpty()) {
      syncSelected();
    } else {
      syncAll();
    }
    LOG.info("{} datasets added, {} updated, {} deleted", created, updated, deleted);
  }

  private void syncSelected() throws Exception {
    for (UUID key : keys) {
      DatasetWithSettings gbif = pager.get(key);
      if (gbif != null) {
        DatasetWithSettings curr = dao.getWithSettings(gbif.getGbifKey());
        sync(gbif, curr);
      }
    }
  }

  private void syncAll() throws Exception {
    final IntSet keys = new IntOpenHashSet();
    LOG.info("Syncing all datasets from GBIF registry {}", cfg.api);
    while (pager.hasNext()) {
      List<DatasetWithSettings> page = pager.next();
      LOG.debug("Received page {} with {} datasets from GBIF", pager.currPageNumber(), page.size());
      for (DatasetWithSettings gbif : page) {
        DatasetWithSettings curr = dao.getWithSettings(gbif.getGbifKey());
        Integer datasetKey = sync(gbif, curr);
        if (datasetKey != null) {
          keys.add(datasetKey);
        }
      }
    }
    // report datasets no longer in GBIF
    try (SqlSession session = sessionFactory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      dm.listGBIF().forEach(key -> {
        if (!keys.contains(key)) {
          // this key was not seen in this registry sync round before - TODO: delete it ???
          Dataset d = dm.get(key);
          LOG.warn("Dataset {} {} missing in GBIF but has key {}", key, d.getTitle(), d.getGbifKey());
        }
      });
    }
  }

  /**
   * @return the dataset key in CLB even if locked or null if it never was synced
   */
  private Integer sync(DatasetWithSettings gbif, DatasetWithSettings curr) throws InterruptedException {
    Exceptions.interruptIfCancelled("GBIF sync interrupted");
    // start out with the existing key if there is one
    Integer key = curr == null ? null : curr.getKey();
    try {
      // a GBIF license is required
      if (gbif.getDataset().getLicense() == null || !gbif.getDataset().getLicense().isCreativeCommons()) {
        LOG.warn("GBIF dataset {} without a creative commons license: {}", gbif.getGbifKey(), gbif.getTitle());

      } else {

        if (curr == null) {
          // create new dataset
          gbif.setCreatedBy(Users.GBIF_SYNC);
          gbif.setModifiedBy(Users.GBIF_SYNC);
          dao.create(gbif, Users.GBIF_SYNC);
          created++;
          key = gbif.getKey();
          LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());

        } else if (curr.isEnabled(Setting.GBIF_SYNC_LOCK)) {
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
          dao.update(curr, Users.GBIF_SYNC);
          updated++;
        }
      }
    } catch (Exception e) {
      if (e instanceof NotUniqueException && gbif.getDataset().getDoi() != null) {
        // treat unique DOI constraints differently as we expect that to happen somtimes
        LOG.warn("Failed to sync GBIF dataset {} >{}<. Non unique DOI {}", gbif.getGbifKey(), gbif.getTitle(), gbif.getDataset().getDoi());
      }
      LOG.error("Failed to sync GBIF dataset {} >{}<", gbif.getGbifKey(), gbif.getTitle(), e);
    }
    return key;
  }
}
