package life.catalogue.gbifsync;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetGBIF;
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

import com.google.common.annotations.VisibleForTesting;

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
  private final GbifRegistryCache registry;
  private Set<UUID> keys;
  private int created;
  private int updated;
  private int deleted;
  private int skipped;
  private boolean incremental;
  private DatasetPager pager;
  // existing ChecklistBank datasets that carry a GBIF key, preloaded once per run to avoid per-dataset DB lookups
  private Map<UUID, DatasetGBIF> existingByGbif = Collections.emptyMap();

  /**
   *  Syncs updates of today
   **/
  public GbifSyncJob(GbifConfig cfg, Client client, DatasetDao ddao, SqlSessionFactory sessionFactory, GbifRegistryCache registry, int userKey, boolean incremental) {
    this(cfg, client, ddao, sessionFactory, registry, userKey, Collections.emptySet(), incremental);
  }

  public GbifSyncJob(GbifConfig cfg, Client client, DatasetDao ddao, SqlSessionFactory sessionFactory, GbifRegistryCache registry, int userKey, Set<UUID> keys, boolean incremental) {
    super(userKey, JobPriority.HIGH);
    this.cfg = cfg;
    this.client = client;
    this.dao = ddao;
    this.sessionFactory = sessionFactory;
    this.registry = registry;
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
    pager = new DatasetPager(client, cfg, since, registry);
    // preload all existing GBIF-keyed datasets once so we avoid a DB lookup per dataset and can skip unchanged ones
    existingByGbif = loadExisting();
    // remove any existing datasets that are blocked - the pager skips them, so they won't be recreated below
    deleteBlocked();
    if (!keys.isEmpty()) {
      syncSelected();
    } else {
      syncAll();
    }
    LOG.info("{} datasets added, {} updated, {} skipped unchanged, {} deleted", created, updated, skipped, deleted);
  }

  /**
   * Loads all non-deleted ChecklistBank datasets that carry a GBIF key into a map keyed by GBIF UUID.
   * Each record carries the GBIF modified timestamp we last synced, used to skip unchanged datasets.
   */
  private Map<UUID, DatasetGBIF> loadExisting() {
    Map<UUID, DatasetGBIF> map = new HashMap<>();
    try (SqlSession session = sessionFactory.openSession()) {
      for (DatasetGBIF d : session.getMapper(DatasetMapper.class).listGBIF()) {
        if (d.getGbifKey() != null) {
          map.put(d.getGbifKey(), d);
        }
      }
    }
    LOG.info("Loaded {} existing GBIF datasets from ChecklistBank", map.size());
    return map;
  }

  /**
   * Deletes any existing ChecklistBank datasets whose GBIF key is configured as blocked.
   * Runs on every sync (incremental and full). Blocked datasets are also filtered out by the pager,
   * so they are never (re)created or updated during the same run.
   */
  private void deleteBlocked() {
    for (UUID gbifKey : cfg.blockedDatasets) {
      DatasetWithSettings curr = dao.getWithSettings(gbifKey);
      if (curr != null) {
        LOG.warn("Delete blocked dataset {} {} with GBIF key {}", curr.getKey(), curr.getTitle(), gbifKey);
        dao.delete(curr.getKey(), Users.GBIF_SYNC);
        deleted++;
      }
    }
  }

  private void syncSelected() throws Exception {
    for (UUID key : keys) {
      var gbif = pager.get(key);
      if (gbif != null) {
        sync(gbif, existingByGbif.get(gbif.getGbifKey()));
      }
    }
  }

  private void syncAll() throws Exception {
    final IntSet seenKeys = new IntOpenHashSet();
    // GBIF keys already processed in this run, so a dataset that reappears on a later page while the
    // live registry changes during paging is skipped instead of being synced (and possibly created) twice
    final Set<UUID> seenGbifKeys = new HashSet<>();
    int count = pager.count();
    LOG.info("Start {} sync of {} datasets from GBIF registry {}", incremental ? "incremental" : "full", count, cfg.api);
    while (pager.hasNext()) {
      List<DatasetPager.GbifDataset> page = pager.next();
      LOG.debug("Received page {} with {} datasets from GBIF", pager.currPageNumber(), page.size());
      for (DatasetPager.GbifDataset gbif : page) {
        if (!seenGbifKeys.add(gbif.getGbifKey())) {
          LOG.debug("Skip GBIF dataset {} already seen earlier during paging", gbif.getGbifKey());
          continue;
        }
        Integer datasetKey = sync(gbif, existingByGbif.get(gbif.getGbifKey()));
        if (datasetKey != null) {
          seenKeys.add(datasetKey.intValue());
        }
      }
    }

    // report datasets no longer in GBIF if we sync all, but not if we only look at todays changes
    if (!incremental) {
      try (SqlSession session = sessionFactory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        dm.listKeysGBIF().forEach(key -> {
          if (!seenKeys.contains((int)key)) {
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
   * @return the dataset key in CLB even if locked/unchanged, or null if it never was synced
   */
  private Integer sync(DatasetPager.GbifDataset gbif, DatasetGBIF existing) throws InterruptedException {
    Exceptions.interruptIfCancelled("GBIF sync interrupted");
    // start out with the existing key if there is one
    Integer key = existing == null ? null : existing.getKey();
    // skip datasets that have not changed in the GBIF registry since we last synced them.
    // this avoids the slow registry organisation/installation lookups and the DB read+write for unchanged datasets.
    if (existing != null && isUnchanged(gbif, existing)) {
      skipped++;
      return key;
    }
    try {
      // a GBIF license is required
      if (gbif.dataset.getLicense() == null || !gbif.dataset.getLicense().isCreativeCommons()) {
        LOG.warn("GBIF dataset {} without a creative commons license: {}", gbif.getGbifKey(), gbif.dataset.getTitle());

      } else {
        // the dataset is new or changed - only now resolve publisher/host/contacts from the slow registry
        pager.resolveAgents(gbif);
        DatasetWithSettings curr = existing == null ? null : dao.getWithSettings(gbif.getGbifKey());

        if (curr == null) {
          // create new dataset
          gbif.dataset.setCreatedBy(Users.GBIF_SYNC);
          gbif.dataset.setModifiedBy(Users.GBIF_SYNC);
          dao.create(gbif.dataset, gbif.settings, Users.GBIF_SYNC);
          LOG.info("New dataset {} added from GBIF: {}", gbif.getKey(), gbif.getTitle());
          created++;
          key = gbif.getKey();
          // keep the preloaded map in sync so the same GBIF key surfacing again later in this run
          // (e.g. a record shifting across a page boundary while the live registry changes during paging)
          // is recognised as existing and not created a second time, which would violate the unique gbif_key constraint
          DatasetGBIF justCreated = new DatasetGBIF();
          justCreated.setKey(key);
          justCreated.setGbifKey(gbif.getGbifKey());
          justCreated.setGbifModified(gbif.getModified());
          existingByGbif.put(gbif.getGbifKey(), justCreated);

        } else if (curr.isEnabled(Setting.GBIF_SYNC_LOCK)) {
          LOG.info("Dataset {} is locked for GBIF updates: {}", gbif.getKey(), gbif.getTitle());

        } else if (!Objects.equals(gbif.getDataAccess(), curr.getDataAccess()) ||
                   !Objects.equals(gbif.dataset.getLicense(), curr.getLicense()) ||
                   !Objects.equals(gbif.dataset.getPublisher(), curr.getPublisher()) ||
                   !Objects.equals(gbif.dataset.getGbifPublisherKey(), curr.getGbifPublisherKey()) ||
                   !Objects.equals(gbif.dataset.getUrl(), curr.getUrl())
        ) {
          // we modify core metadata (title, description, contacts, version) via the dwc archive metadata
          // gbif syncs only change one of the following
          // - dwca/coldp access url
          // - license
          // - publisher (publishOrgKey)
          // - gbif publisher key
          // - homepage
          curr.setDataAccess(gbif.getDataAccess());
          curr.setDataFormat(gbif.getDataFormat());
          curr.setLicense(gbif.dataset.getLicense());
          curr.setPublisher(gbif.dataset.getPublisher());
          curr.setGbifPublisherKey(gbif.dataset.getGbifPublisherKey());
          curr.setUrl(gbif.dataset.getUrl());
          dao.update(curr, Users.GBIF_SYNC);
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
        // remember the GBIF modified timestamp we just synced so this dataset is skipped next run until it changes again.
        // done for create/update/locked/no-op alike so we never re-examine a dataset we have already processed.
        if (key != null) {
          persistGbifModified(key, gbif.getModified());
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to sync GBIF dataset {} >{}<", gbif.getGbifKey(), gbif.getTitle(), e);
    }
    return key;
  }

  /**
   * @return true if the GBIF dataset has not been modified in the registry since we last synced it.
   *   When either timestamp is unknown (e.g. datasets synced before delta tracking) we treat it as changed
   *   so it gets processed once and its watermark recorded.
   */
  @VisibleForTesting
  static boolean isUnchanged(DatasetPager.GbifDataset gbif, DatasetGBIF existing) {
    LocalDateTime gMod = gbif.getModified();
    LocalDateTime stored = existing.getGbifModified();
    return gMod != null && stored != null && !gMod.isAfter(stored);
  }

  /** Persists the GBIF registry modified timestamp we last synced for a dataset. */
  private void persistGbifModified(int key, LocalDateTime modified) {
    if (modified == null) {
      return;
    }
    try (SqlSession session = sessionFactory.openSession(true)) {
      session.getMapper(DatasetMapper.class).updateGbifModified(key, modified);
    }
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
