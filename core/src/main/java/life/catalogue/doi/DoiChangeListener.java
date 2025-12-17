package life.catalogue.doi;

import life.catalogue.api.event.DoiChange;
import life.catalogue.api.event.DoiListener;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that listens to DoiChange events, persists them and updates
 * DataCite accordingly. In case of errors or restarts retries to apply changes to DataCite.
 */
public class DoiChangeListener implements DoiListener {
  private static final Logger LOG = LoggerFactory.getLogger(DoiChangeListener.class);
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final Set<DOI> deleted = ConcurrentHashMap.newKeySet();
  private final LatestDatasetKeyCache datasetKeyCache;
  private final DoiConfig cfg;

  public DoiChangeListener(SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache, DatasetConverter converter, DoiConfig cfg) {
    this.factory = factory;
    this.doiService = doiService;
    this.converter = converter;
    this.datasetKeyCache = datasetKeyCache;
    this.cfg = cfg;
  }

  /**
   * Updates or deletes the DOI metadata in DataCite. This can happen if dataset metadata has changed but also if a release was added or removed.
   * In case an entire project gets deleted
   * which removed the sources already from the DB and cascades a project deletion to all its releases!!!
   */
  @Override
  public void doiChanged(DoiChange event){
    //TODO: persist on disk and create cron job every x minutes to process & distinct events
    if (event.getDoi().getPrefix().equalsIgnoreCase(cfg.prefix)) {
      try {
        switch (event.getType()) {
          case CREATE -> doiService.create(metadata(event.getDoi()));
          case DELETE -> delete(event.getDoi());
          case PUBLISH -> doiService.publish(event.getDoi());
          case UPDATE -> doiService.update(metadata(event.getDoi()));
        }
      } catch (Exception e) {
        LOG.error("Error processing {} DOI event for DOI {}", event.getType(), event.getDoi(), e);
      }
    }
  }

  private DoiAttributes metadata(DOI doi) {
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var dam = session.getMapper(DatasetArchiveMapper.class);
      var dim = session.getMapper(DatasetImportMapper.class);

      if (doi.isDatasetAttempt()) {
        var key = doi.datasetAttemptKey();
        Dataset d = dam.get(doi.datasetKey(), key.getId());
        var prevImp = dim.getLast(key.getDatasetKey(), key.getId(), ImportState.FINISHED);
        var nextImp = dim.getNext(key.getDatasetKey(), key.getId(), ImportState.FINISHED);
        DOI prev = prevImp == null ? null : cfg.datasetDOI(d.getKey(), prevImp.getAttempt());
        DOI next = nextImp == null ? null : cfg.datasetDOI(d.getKey(), nextImp.getAttempt());
        return converter.datasetAttempt(d, prev, next);

      } else if (doi.isDatasetSource()) {
        throw new IllegalArgumentException("Source dataset DOIs not supported any longer: " + doi);

      } else {
        Dataset d = dm.get(doi.datasetKey());
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
    if (doi.isDatasetAttempt()) {
      var key = doi.datasetAttemptKey();
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

  private void delete(DOI doi) throws DoiException {
    // if the release was still private, it only had a draft DOI which gets removed completely
    if (!doiService.delete(doi)) {
      // DOI was hidden only - make sure the URL is correct and points to CLB
      doiService.update(doi, url(doi));
    }
    deleted.add(doi);
  }
}
