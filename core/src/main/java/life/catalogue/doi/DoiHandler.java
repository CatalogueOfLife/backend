package life.catalogue.doi;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.Managed;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.*;
import life.catalogue.outbox.DoiAction;
import life.catalogue.outbox.Outbox;
import life.catalogue.outbox.OutboxHandler;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the DOI commands of the outbox against DataCite.
 *
 * There is one command per DOI and the requested actions are flags of it, see {@link DoiAction}: a delete runs alone,
 * a create already sends the current metadata and publishes the DOI if the dataset is public, an update sends the
 * metadata and a publish makes the DOI findable. Metadata is always read when the command runs, so a burst of updates
 * collapses into a single call, and every step is safe to repeat: a create of an existing DOI updates it instead.
 *
 * This is the DoiUpdater component. While it is stopped the DOI commands wait in the outbox, so DataCite can be
 * paused without losing a change. A handler that is always online drains the commands of a server that must never
 * talk to DataCite, with a pass through DoiService.
 */
public class DoiHandler implements OutboxHandler, Managed {
  private static final Logger LOG = LoggerFactory.getLogger(DoiHandler.class);
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final LatestDatasetKeyCache datasetKeyCache;
  private final DoiConfig cfg;
  private final boolean alwaysOnline;
  private volatile boolean started;

  public DoiHandler(SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache, DatasetConverter converter,
                    DoiConfig cfg) {
    this(factory, doiService, datasetKeyCache, converter, cfg, false);
  }

  /**
   * @param alwaysOnline if true the handler runs commands without being started. Use it only with a pass through
   *                     DoiService, for a server whose DoiUpdater component is disabled
   */
  public DoiHandler(SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache, DatasetConverter converter,
                    DoiConfig cfg, boolean alwaysOnline) {
    this.factory = factory;
    this.doiService = doiService;
    this.converter = converter;
    this.datasetKeyCache = datasetKeyCache;
    this.cfg = cfg;
    this.alwaysOnline = alwaysOnline;
  }

  @Override
  public void start() {
    started = true;
    LOG.info("Start DOI updates with prefix {}", cfg.prefix);
  }

  @Override
  public void stop() {
    started = false;
    LOG.info("Stop DOI updates, DOI commands wait in the outbox");
  }

  @Override
  public boolean hasStarted() {
    return started;
  }

  @Override
  public boolean isOnline() {
    return alwaysOnline || started;
  }

  /**
   * Bounds the load on DataCite.
   */
  @Override
  public int concurrency() {
    return 2;
  }

  /**
   * Waits attempts squared hours, giving DataCite time to recover.
   */
  @Override
  public Duration retryDelay(int attempts) {
    return Duration.ofHours((long) attempts * attempts);
  }

  /**
   * Metadata DataCite rejects stays rejected until the dataset changes, which revives the command with a new request.
   */
  @Override
  public boolean isPermanent(Exception e) {
    return e instanceof InvalidMetadataException
      || (e instanceof DoiHttpException he && he.getStatus() == 422)
      || e instanceof IllegalArgumentException;
  }

  @Override
  public void onFailed(OutboxEntry entry, Exception e) {
    doiService.notifyException(new DOI(entry.getKey()), Outbox.doiActions(entry.getParams()).toString(), e);
  }

  @Override
  public void handle(OutboxEntry entry) throws DoiException {
    final DOI doi = new DOI(entry.getKey());
    // make sure it is a DOI with our prefix
    if (!doi.getPrefix().equalsIgnoreCase(cfg.prefix)) {
      LOG.info("Ignore DOI {} with a prefix other than {}", doi, cfg.prefix);
      return;
    }
    // make sure it is a real dataset
    final int key = doi.datasetKey();
    try (SqlSession session = factory.openSession()) {
      if (session.getMapper(DatasetMapper.class).getSimple(key) == null) {
        LOG.warn("Ignore DOI {} of unknown dataset {}", doi, key);
        return;
      }
    }

    final Set<DoiAction> actions = Outbox.doiActions(entry.getParams());
    LOG.info("{} DOI {} of dataset {}", actions, doi, key);
    if (actions.contains(DoiAction.DELETE)) {
      delete(doi);
      return;
    }
    boolean metadataSent = false;
    boolean published = false;
    if (actions.contains(DoiAction.CREATE)) {
      published = create(doi);
      metadataSent = true;
    }
    if (actions.contains(DoiAction.UPDATE) && !metadataSent) {
      doiService.update(metadata(doi));
    }
    if (actions.contains(DoiAction.PUBLISH) && !published) {
      doiService.publish(doi);
    }
  }

  /**
   * @return true if the DOI was published too
   */
  private boolean create(DOI doi) throws DoiException {
    var attr = metadata(doi);
    try {
      doiService.create(attr);
    } catch (DoiExistsException e) {
      // an earlier run of this very command that failed after the DOI was registered got there first.
      // Converge on the metadata we want instead of failing - 422 is not retried, so a create we cannot
      // repeat would otherwise be stuck for good.
      LOG.info("DOI {} exists at DataCite already, update its metadata instead", doi);
      doiService.update(attr);
    }
    if (isPublic(doi.datasetKey())) {
      doiService.publish(doi);
      return true;
    }
    return false;
  }

  private boolean isPublic(int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      return !session.getMapper(DatasetMapper.class).isPrivate(datasetKey);
    }
  }

  private void delete(DOI doi) throws DoiException {
    // if the dataset was still private, it only had a draft DOI which gets removed completely
    if (!doiService.delete(doi)) {
      // ... otherwise the DOI was hidden only - make sure the URL is correct and points to CLB
      doiService.update(doi, url(doi));
    }
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
        return converter.releaseURI(key, info.sourceKey, datasetKeyCache.isLatestRelease(key));
      }
      return converter.datasetURI(key);
    }
  }
}
