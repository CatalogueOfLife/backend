package life.catalogue.doi;

import com.esotericsoftware.kryo.Kryo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.event.DoiChange;
import life.catalogue.api.event.DoiListener;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.HasID;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.cache.ObjectCache;
import life.catalogue.cache.ObjectCacheMapDB;
import life.catalogue.common.Managed;
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
import life.catalogue.doi.service.DoiExistsException;
import life.catalogue.doi.service.DoiHttpException;
import life.catalogue.doi.service.DoiService;
import life.catalogue.doi.service.InvalidMetadataException;

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
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that listens to DoiChange events, persists them and updates
 * DataCite accordingly. In case of errors or restarts retries to apply changes to DataCite.
 */
public class DoiChangeListener implements DoiListener, Managed {
  private static final Logger LOG = LoggerFactory.getLogger(DoiChangeListener.class);
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final Set<DOI> deleted = ConcurrentHashMap.newKeySet();
  private final LatestDatasetKeyCache datasetKeyCache;
  private final DoiConfig cfg;
  private final long wait;
  private ObjectCache<XDoiChange> events; // tmp persisted cache
  private ScheduledExecutorService scheduler;
  private ExecutorService executor;

  public DoiChangeListener(SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache, DatasetConverter converter, DoiConfig cfg) {
    this.factory = factory;
    this.doiService = doiService;
    this.converter = converter;
    this.datasetKeyCache = datasetKeyCache;
    this.cfg = cfg;
    this.wait = TimeUnit.SECONDS.toMillis(cfg.waitPeriod);
  }

  /**
   * Only a started listener ever talks to DataCite. This is a stoppable component on purpose: during a
   * blue-green deploy both the old and the new app read every event from the shared broker queue, so the
   * old one has to be told to stop acting on DOI changes before the new one takes over - otherwise both
   * create the same DOI and whoever loses the race gets a 422 "This DOI has already been taken".
   */
  @Override
  public void start() throws Exception {
    if (hasStarted()) {
      return;
    }
    // to avoid collision with running apps in parallel during deploys we create new event stores
    this.events = new ObjectCacheMapDB<>(XDoiChange.class, freeStoreFile(new File(cfg.store)), new DOIKryoPool(), true);
    LOG.info("Start DOI listener executing changes every {} minutes from {}", TimeUnit.SECONDS.toMinutes(cfg.waitPeriod), cfg.store);
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.scheduler = Executors.newScheduledThreadPool(1,
      new NamedThreadFactory("doi-updater", Thread.NORM_PRIORITY, true)
    );
    scheduler.scheduleAtFixedRate(new UpdateJob(), 0, cfg.waitPeriod/2, TimeUnit.SECONDS);
  }

  @Override
  public void stop() throws Exception {
    if (!hasStarted()) {
      return;
    }
    ExecutorUtils.shutdown(scheduler, 10, TimeUnit.SECONDS);
    scheduler = null;
    executor.close();
    executor = null;
    if (events.size()>0) {
      LOG.warn("Stopping DOI change listener with {} DOI events waiting", events.size());
      for (XDoiChange event : events) {
        LOG.info("Discard queued DOI {} for dataset {}: {}", event.getType(), event.datasetKey, event.getDoi());
      }
    }
    events.close();
    events = null;
  }

  @Override
  public boolean hasStarted() {
    return events != null;
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
      // a stopped listener - an old app during a deploy - must not touch DataCite at all.
      // Read both fields once: stop() nulls them and we must not blow up on an event that races a shutdown.
      final ObjectCache<XDoiChange> store = this.events;
      final ExecutorService exec = this.executor;
      if (store == null || exec == null) {
        LOG.info("DOI listener not running, ignore {} event for DOI {}", event.getType(), event.getDoi());
        return;
      }
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
        store.put(xevent);
      } else {
        // execute immediately
        exec.submit(new DoiChangeJob(xevent));
      }

    } catch (RuntimeException e) {
      LOG.error("Failed to process DOI change event {}", event, e);
    }
  }

  public List<XDoiChange> list() {
    return hasStarted() ? events.list() : Collections.emptyList();
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
        var prevImp = dim.getLast(key.getDatasetKey(), key.getId(), JobStatus.FINISHED);
        var nextImp = dim.getNext(key.getDatasetKey(), key.getId(), JobStatus.FINISHED);
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
      if (!all.isEmpty()) {
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
      } catch (InvalidMetadataException e) {
        // pre-flight validation failed — permanent failure, do not re-queue
        LOG.error("Permanent metadata failure for {} event on DOI {} — not retrying: {}", event.getType(), event.getDoi(), e.getMessage());
        doiService.notifyException(event.getDoi(), event.getType().name(), e);
      } catch (DoiHttpException e) {
        if (e.getStatus() == 422) {
          // DataCite rejected metadata as invalid — permanent failure, do not re-queue
          LOG.error("DataCite rejected metadata (HTTP 422) for {} event on DOI {} — not retrying: {}", event.getType(), event.getDoi(), e.getMessage());
          doiService.notifyException(event.getDoi(), event.getType().name(), e);
        } else {
          LOG.error("HTTP {} error processing {} DOI event for DOI {}", e.getStatus(), event.getType(), event.getDoi(), e);
          events.put(new XDoiChange(event, event.fails+1));
        }
      } catch (Exception e) {
        LOG.error("Error processing {} DOI event for DOI {}", event.getType(), event.getDoi(), e);
        events.put(new XDoiChange(event, event.fails+1));
      }
    }

    private void create(DOI doi) throws DoiException {
      var attr = metadata(doi);
      try {
        doiService.create(attr);
      } catch (DoiExistsException e) {
        // another app instance, or an earlier run of this very event that failed after the DOI was
        // already registered, got there first. Converge on the metadata we want instead of failing -
        // a create we cannot repeat is otherwise stuck for good, as 422 is not retried.
        LOG.info("DOI {} exists at DataCite already, update its metadata instead", doi);
        doiService.update(attr);
      }
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
}
