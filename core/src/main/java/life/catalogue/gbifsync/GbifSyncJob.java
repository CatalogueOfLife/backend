package life.catalogue.gbifsync;

import life.catalogue.api.exception.NotUniqueException;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.GlobalBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.BasicAuthenticator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;

public class GbifSyncJob extends GlobalBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(GbifSyncJob.class);
  static final String CLB_DATASET_KEY = "CLB_DATASET_KEY";

  private final Client client;
  private final String auth;
  private final WebTarget datasetTarget;
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
    this.datasetTarget = client.target(UriBuilder
      .fromUri(cfg.api)
      .path("/dataset")
    );
    if (cfg.username != null && cfg.password != null) {
      auth = BasicAuthenticator.basicAuthentication(cfg.username, cfg.password);
    } else {
      auth = null;
    }
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
      var gbif = pager.get(key);
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
      List<DatasetPager.GbifDataset> page = pager.next();
      LOG.debug("Received page {} with {} datasets from GBIF", pager.currPageNumber(), page.size());
      for (DatasetPager.GbifDataset gbif : page) {
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
  private Integer sync(DatasetPager.GbifDataset gbif, DatasetWithSettings curr) throws InterruptedException {
    Exceptions.interruptIfCancelled("GBIF sync interrupted");
    // start out with the existing key if there is one
    Integer key = curr == null ? null : curr.getKey();
    try {
      // a GBIF license is required
      if (gbif.dataset.getLicense() == null || !gbif.dataset.getLicense().isCreativeCommons()) {
        LOG.warn("GBIF dataset {} without a creative commons license: {}", gbif.getGbifKey(), gbif.dataset.getTitle());

      } else {

        if (curr == null) {
          // create new dataset
          gbif.dataset.setCreatedBy(Users.GBIF_SYNC);
          gbif.dataset.setModifiedBy(Users.GBIF_SYNC);
          try {
            dao.create(gbif.dataset, gbif.settings, Users.GBIF_SYNC);
          } catch (NotUniqueException e) {
            // catch DOI unique constraint errors and try again without the DOI
            // in the GBIF registry, especially with Plazi datasets, it happens that multiple datasets have the same DOI!
            DOI doi = gbif.dataset.getDoi();
            if (doi != null) {
              gbif.dataset.setDoi(null);
              var dk = dao.create(gbif.dataset, gbif.settings, Users.GBIF_SYNC);
              LOG.warn("Removed non unique DOI {} from newly created dataset {}: {}", doi, dk, gbif.getTitle());
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
                   !Objects.equals(gbif.dataset.getLicense(), curr.getLicense()) ||
                   !Objects.equals(gbif.dataset.getPublisher(), curr.getPublisher()) ||
                   !Objects.equals(gbif.dataset.getGbifPublisherKey(), curr.getGbifPublisherKey()) ||
                   !Objects.equals(gbif.dataset.getUrl(), curr.getUrl()) ||
                   !Objects.equals(gbif.dataset.getDoi(), curr.getDoi())
        ) {
          // we modify core metadata (title, description, contacts, version) via the dwc archive metadata
          //gbif syncs only change one of the following
          // - dwca access url
          // - license
          // - publisher (publishOrgKey)
          // - gbif publisher key
          // - homepage
          // - doi
          curr.setDataAccess(gbif.getDataAccess());
          curr.setDataFormat(gbif.getDataFormat());
          curr.setLicense(gbif.dataset.getLicense());
          curr.setPublisher(gbif.dataset.getPublisher());
          curr.setGbifPublisherKey(gbif.dataset.getGbifPublisherKey());
          curr.setUrl(gbif.dataset.getUrl());
          curr.setDoi(gbif.dataset.getDoi());
          try {
            dao.update(curr, Users.GBIF_SYNC);
          } catch (NotUniqueException e) {
            // catch DOI unique constraint errors and try again without the DOI
            // in the GBIF registry, especially with Plazi datasets, it happens that multiple datasets have the same DOI!
            DOI doi = curr.getDoi();
            if (doi != null) {
              curr.setDoi(null);
              dao.update(curr, Users.GBIF_SYNC);
              LOG.warn("Removed non unique DOI {} from updated dataset {}: {}", doi, curr.getKey(), gbif.getTitle());
            } else {
              throw e;
            }
          }
          updated++;
        } else if (curr.getType() == DatasetType.ARTICLE &&
          (curr.getAlias() == null || curr.getAlias() != null && curr.getAlias().endsWith(curr.getKey().toString()))
        ) {
          // set new alias if we have the old form with dataset key still for articles
          curr.setAlias(null);
          dao.update(curr, Users.GBIF_SYNC);
        }
        // let the registry track CLB dataset keys
        if (cfg.bidirectional) {
          if (gbif.clbDatasetKey == null) {
            addDatasetKey(gbif.getGbifKey(), key);
          } else if (!gbif.clbDatasetKey.equals(key)) {
            LOG.info("Update changed dataset key in registry for {} to {}", gbif.getGbifKey(), key);
            delDatasetKeys(gbif.getGbifKey(), gbif.identifierKeys);
            addDatasetKey(gbif.getGbifKey(), key);
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to sync GBIF dataset {} >{}<", gbif.getGbifKey(), gbif.getTitle(), e);
    }
    return key;
  }

  private void addDatasetKey(UUID key, Integer datasetKey) {
    if (key != null && datasetKey != null && auth != null) {
      DatasetPager.GIdentifier gIdentifier = new DatasetPager.GIdentifier();
      gIdentifier.type = CLB_DATASET_KEY;
      gIdentifier.identifier = datasetKey.toString();

      LOG.info("Add CLB dataset key {} to GBIF checklist {} in registry", datasetKey, key);
      var target = datasetTarget.path(key + "/identifier");
      try (var resp = target.request()
        .accept(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, auth)
        .post(Entity.json(gIdentifier))) {
      }
    }
  }

  private void delDatasetKeys(UUID datasetKey, List<Integer> identifierKeys) {
    if (identifierKeys != null) {
      for (Integer idKey : identifierKeys) {
        LOG.info("Delete existing CLB_DATASET_KEY identifier {} in GBIF for checklist {}", idKey, datasetKey);
        var target = datasetTarget.path(datasetKey + "/identifier/" + idKey);
        try (var resp = target.request()
          .accept(MediaType.APPLICATION_JSON_TYPE)
          .header(HttpHeaders.AUTHORIZATION, auth)
          .delete()) {
        }
      }
    }
  }

}
