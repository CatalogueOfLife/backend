package life.catalogue.gbifsync;

import life.catalogue.api.exception.NotUniqueException;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.DatasetMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import jakarta.ws.rs.client.Client;

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
  private boolean incremental;
  private DatasetPager pager;

  /**
   *  Syncs updates of today
   **/
  public GbifSyncJob(GbifConfig cfg, Client client, DatasetDao ddao, SqlSessionFactory sessionFactory, int userKey, boolean incremental) {
    this(cfg, client, ddao, sessionFactory, userKey, Collections.emptySet(), incremental);
  }

  public GbifSyncJob(GbifConfig cfg, Client client, DatasetDao ddao, SqlSessionFactory sessionFactory, int userKey, Set<UUID> keys, boolean incremental) {
    super(userKey, JobPriority.HIGH);
    this.cfg = cfg;
    this.client = client;
    this.dao = ddao;
    this.sessionFactory = sessionFactory;
    this.keys = keys == null ? new HashSet<>() : keys;
    this.incremental = incremental;
  }

  @Override
  public void execute() throws Exception {
    LocalDate since = null;
    if (incremental) {
      if (LocalDateTime.now().getHour() == 0) {
        // in the first hour after midnight we might miss changes happening between the last sync of the previous day and the first of the new day
        // so lets sync since 2 days ago then
        since = LocalDate.now().minusDays(1);
      } else {
        // normally we just look for changes of the day
        since = LocalDate.now();
      }
    }
    pager = new DatasetPager(client, cfg, since);
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
    int count = pager.count();
    LOG.info("Start {} sync of {} datasets from GBIF registry {}", incremental ? "incremental" : "full", count, cfg.api);
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

    // report datasets no longer in GBIF if we sync all, but not if we only look at todays changes
    if (!incremental) {
      try (SqlSession session = sessionFactory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        dm.listKeysGBIF().forEach(key -> {
          if (!keys.contains((int)key)) {
            // this key was not seen in this registry sync round before - delete it
            Dataset d = dm.get(key);
            if (d.getOrigin() != DatasetOrigin.EXTERNAL) {
              LOG.warn("Dataset {} {} has GBIF key {}, but is of origin {}", key, d.getTitle(), d.getGbifKey(), d.getOrigin());
            } else if(d.getCreated().plusHours(12).isAfter(LocalDateTime.now())) {
              LOG.info("Potentially deleted GBIF dataset found that was created within the last 12h. Keep it for now: {} {} with GBIF key {}", key, d.getTitle(), d.getGbifKey());
            } else {
              LOG.warn("Delete dataset {} {} with GBIF key {} which was removed in GBIF", key, d.getTitle(), d.getGbifKey());
              dao.delete(key, Users.GBIF_SYNC);
              deleted++;
            }
          }
        });
      }
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
          try {
            dao.create(gbif, Users.GBIF_SYNC);
          } catch (NotUniqueException e) {
            // catch DOI unique constraint errors and try again without the DOI
            // in the GBIF registry, especially with Plazi datasets, it happens that multiple datasets have the same DOI!
            DOI doi = gbif.getDoi();
            if (doi != null) {
              gbif.setDoi(null);
              dao.create(gbif, Users.GBIF_SYNC);
              LOG.warn("Non unique DOI {} in dataset {}: {}", doi, gbif.getKey(), gbif.getTitle());
            } else {
              throw e;
            }
          }
          LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());
          created++;
          key = gbif.getKey();

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
          try {
            dao.update(curr, Users.GBIF_SYNC);
          } catch (NotUniqueException e) {
            // catch DOI unique constraint errors and try again without the DOI
            // in the GBIF registry, especially with Plazi datasets, it happens that multiple datasets have the same DOI!
            DOI doi = curr.getDoi();
            if (doi != null) {
              curr.setDoi(null);
              dao.update(curr, Users.GBIF_SYNC);
              LOG.warn("Non unique DOI {} in dataset {}: {}", doi, gbif.getKey(), gbif.getTitle());
            } else {
              throw e;
            }
          }
          updated++;
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to sync GBIF dataset {} >{}<", gbif.getGbifKey(), gbif.getTitle(), e);
    }
    return key;
  }

}
