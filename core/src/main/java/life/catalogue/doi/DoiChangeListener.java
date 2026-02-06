package life.catalogue.doi;

import com.esotericsoftware.kryo.Kryo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.event.DoiChange;
import life.catalogue.api.event.DoiListener;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.HasID;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.cache.ObjectCache;
import life.catalogue.cache.ObjectCacheMapDB;
import life.catalogue.common.kryo.ApiKryoPool;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that listens to DoiChange events, persists them and updates
 * DataCite accordingly. In case of errors or restarts retries to apply changes to DataCite.
 */
public class DoiChangeListener implements DoiListener, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(DoiChangeListener.class);
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final Set<DOI> deleted = ConcurrentHashMap.newKeySet();
  private final LatestDatasetKeyCache datasetKeyCache;
  private final DoiConfig cfg;
  private final ObjectCache<XDoiChange> events; // tmp persisted cache
  private final long wait;
  private final ScheduledExecutorService scheduler;
  private final ExecutorService executor;

  public DoiChangeListener(SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache, DatasetConverter converter, DoiConfig cfg) throws IOException {
    this.factory = factory;
    this.doiService = doiService;
    this.converter = converter;
    this.datasetKeyCache = datasetKeyCache;
    this.cfg = cfg;
    this.wait = TimeUnit.SECONDS.toMillis(cfg.waitPeriod);
    // to avoid collision with running apps in parallel during deploys we create new event stores
    this.events = new ObjectCacheMapDB<>(XDoiChange.class, freeStoreFile(new File(cfg.store)), new DOIKryoPool(), true);
    LOG.info("Start DOI listener executing changes every {} minutes from {}", TimeUnit.SECONDS.toMinutes(cfg.waitPeriod), cfg.store);
    this.scheduler = Executors.newScheduledThreadPool(1,
      new NamedThreadFactory("doi-updater", Thread.NORM_PRIORITY, true)
    );
    scheduler.scheduleAtFixedRate(new UpdateJob(), 0, cfg.waitPeriod/2, TimeUnit.SECONDS);
    executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  protected static File freeStoreFile(File dir) throws IOException {
    FileUtils.forceMkdir(dir);
    int x = 1;
    File f = null;
    while (f == null || f.exists()) {
      f = new File(dir, "event-" + x++);
    }
    return f;
  }

  /**
   * Updates or deletes the DOI metadata in DataCite. This can happen if dataset metadata has changed but also if a release was added or removed.
   * In case an entire project gets deleted
   * which removed the sources already from the DB and cascades a project deletion to all its releases!!!
   */
  @Override
  public void doiChanged(DoiChange event) {
    try {
      // make sure it is a DOI with our prefix
      if (!event.getDoi().getPrefix().equalsIgnoreCase(cfg.prefix)) {
        LOG.info("Ignore {} event for DOI {} with wrong DOI prefix for this config", event.getType(), event.getDoi());
        return;
      }

      // make sure it is a real dataset
      final int key = event.getDoi().datasetKey();
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        var d = dm.getSimple(key);
        if (d == null) {
          LOG.warn("Ignore {} event for DOI {} with unknown dataset key {}", event.getType(), event.getDoi(), key);
          return;
        }
      }

      var xevent = new XDoiChange(event, key, wait);
      if (event.isUpdate()) {
        // pool updates for some time
        // this overrides potentially already waiting events for the same DOI and type
        events.put(xevent);
      } else {
        // execute immediately
        executor.submit(new DoiChangeJob(xevent));
      }

    } catch (RuntimeException e) {
      LOG.error("Failed to process DOI change event {}", event, e);
    }
  }

  public List<XDoiChange> list() {
    return events.list();
  }

  private DoiAttributes metadata(DOI doi) throws DoiException {
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var dam = session.getMapper(DatasetArchiveMapper.class);
      var dim = session.getMapper(DatasetImportMapper.class);

      if (doi.isDatasetVersion()) {
        var key = doi.datasetVersionKey();
        // for the latest attempt we need to consult the live dataset, older ones are in the archive
        // as we mostly create version DOIs on import, we first try the main dataset
        Dataset d = dm.get(doi.datasetKey());
        if (d == null || !Objects.equals(d.getAttempt(), key.getId())) {
          // try archive
          d = dam.get(doi.datasetKey(), key.getId());
        }
        if (d == null) {
          throw new DoiException(doi, "Can't find the metadata for dataset import " + key);
        }
        d.setVersionDoi(doi);
        var prevImp = dim.getLast(key.getDatasetKey(), key.getId(), ImportState.FINISHED);
        var nextImp = dim.getNext(key.getDatasetKey(), key.getId(), ImportState.FINISHED);
        DOI prev = prevImp == null ? null : cfg.datasetVersionDOI(d.getKey(), prevImp.getAttempt());
        DOI next = nextImp == null ? null : cfg.datasetVersionDOI(d.getKey(), nextImp.getAttempt());
        return converter.datasetVersion(d, prev, next);

      } else if (doi.isDatasetSource()) {
        throw new IllegalArgumentException("Source dataset DOIs not supported any longer: " + doi);

      } else {
        Dataset d = dm.get(doi.datasetKey());
        if (d == null) {
          throw new DoiException(doi, "Can't find the metadata for dataset " + doi.datasetKey());
        }
        d.setDoi(doi);
        if (d.getOrigin().isRelease()) {
          var prevKey = dm.previousRelease(d.getKey());
          var nextKey = dm.nextRelease(d.getKey());
          DOI prev = prevKey == null ? null : cfg.datasetDOI(prevKey);
          DOI next = nextKey == null ? null : cfg.datasetDOI(nextKey);
          return converter.release(d, nextKey==null, prev, next);

        } else {
          return converter.dataset(d);
        }
      }
    }
  }

  private URI url(DOI doi) {
    if (doi.isDatasetVersion()) {
      var key = doi.datasetVersionKey();
      return converter.attemptURI(key.getDatasetKey(), key.getId());
    } else if (doi.isDatasetSource()) {
      var key = doi.sourceDatasetKey();
      return converter.sourceURI(key.getDatasetKey(), key.getId());
    } else {
      int key = doi.datasetKey();
      var info = DatasetInfoCache.CACHE.info(key, true);
      if (info.origin.isRelease()) {
        return converter.releaseURI(key, datasetKeyCache.isLatestRelease(key));
      }
      return converter.datasetURI(key);
    }
  }

  private static class DOIKryoPool extends ApiKryoPool {
    @Override
    public Kryo create() {
      var k = super.create();
      k.register(DoiChange.class);
      k.register(DoiChange.DoiEventType.class);
      k.register(DoiChangeListener.XDoiChange.class);
      return k;
    }
  }
  public static class XDoiChange extends DoiChange implements HasID<String>, Comparable<XDoiChange> {
    public int datasetKey;
    public long time;
    public int fails;

    public XDoiChange() {
    }

    XDoiChange(DoiChange event, int datasetKey, long wait) {
      super(event.getDoi(), event.getType());
      this.datasetKey = datasetKey;
      this.fails = 0;
      time = System.currentTimeMillis() + wait;
    }

    XDoiChange(XDoiChange event, int fails) {
      super(event.getDoi(), event.getType());
      this.datasetKey = event.datasetKey;
      this.fails = fails;
      time = System.currentTimeMillis() + TimeUnit.HOURS.toMillis((long) fails*fails);
    }

    @Override
    @JsonIgnore
    public String getId() {
      return getType().name().charAt(0) + "@" + getDoi().getDoiName();
    }

    @Override
    public int compareTo(@NotNull XDoiChange o) {
      return Long.compare(time, o.time);
    }

    @Override
    @JsonIgnore
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(getId());
      sb.append(" Key=").append(datasetKey);
      if (fails > 0) {
        sb.append(" #").append(fails);
      }
      return sb.toString();
    }
  }

  private class UpdateJob implements Runnable {
    @Override
    public void run() {
      List<XDoiChange> all = list();
      Collections.sort(all);
      int counter = 0;
      LOG.debug("Found {} DOI events to process", all.size());
      for (XDoiChange event : all) {
        if (event.time < System.currentTimeMillis()) {
          events.remove(event.getId());
          executor.submit(new DoiChangeJob(event));
          counter++;
        }
      }
      if (counter > 0) {
        LOG.info("Executed {} DOI events from {}", counter, all.size());
      }
    }
  }

  private class DoiChangeJob implements Runnable {
    final XDoiChange event;

    public DoiChangeJob(XDoiChange event) {
      this.event = event;
    }

    @Override
    public void run() {
      try {
        switch (event.getType()) {
          case CREATE -> create(event.getDoi());
          case DELETE -> delete(event.getDoi());
          case PUBLISH -> publish(event.getDoi());
          case UPDATE -> doiService.update(metadata(event.getDoi()));
        }
      } catch (Exception e) {
        LOG.error("Error processing {} DOI event for DOI {}", event.getType(), event.getDoi(), e);
        events.put(new XDoiChange(event, event.fails+1));
      }
    }

    private void create(DOI doi) throws DoiException {
      doiService.create(metadata(doi));
      if (isPublic(event.datasetKey)) {
        publish(doi);
      }
    }

    private void publish(DOI doi) throws DoiException {
      doiService.publish(doi);
    }

    private boolean isPublic(int datasetKey) {
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        return !dm.isPrivate(datasetKey);
      }
    }

    private void delete(DOI doi) throws DoiException {
      // if the dataset was still private, it only had a draft DOI which gets removed completely
      if (!doiService.delete(doi)) {
        // ... otherwise the DOI was hidden only - make sure the URL is correct and points to CLB
        doiService.update(doi, url(doi));
      }
      deleted.add(doi);
    }
  }

  @Override
  public void close() throws Exception {
    ExecutorUtils.shutdown(scheduler, 10, TimeUnit.SECONDS);
    executor.close();
    if (events.size()>0) {
      LOG.warn("Closing DOI change listener with {} DOI events waiting", events.size());
      for (XDoiChange event : events) {
        LOG.info("Discard queued DOI {} for dataset {}: {}", event.getType(), event.datasetKey, event.getDoi());
      }
    }
    events.close();
  }
}
