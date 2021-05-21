package life.catalogue.doi;

import life.catalogue.WsServerConfig;
import life.catalogue.api.event.DoiChange;

import com.google.common.eventbus.Subscribe;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to update or delete DOI metadata when datasets change or get deleted.
 * DOIs for releases and projects are 1:1, so its simple to delete or update them.
 *
 * For release sources things are more complex as we share the same DOI for the same source in multiple releases
 * in case the source data has not changed. This means the same DOI applies to multiple archived source datasets.
 *
 * As a rule the URL designated in the DOI metadata should always refer to the earliest release when the DOI was first minted.
 * TODO: We might need to rethink this rule and point it to the first annual release (if existing) as these releases will not be deleted.
 */
public class DoiUpdater {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdater.class);
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final Set<DOI> deleted = ConcurrentHashMap.newKeySet();
  private final LatestDatasetKeyCache datasetKeyCache;

  public DoiUpdater(WsServerConfig cfg, SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache) {
    this.cfg = cfg;
    this.factory = factory;
    this.doiService = doiService;
    this.converter = new DatasetConverter(cfg.portalURI, cfg.clbURI);
    this.datasetKeyCache = datasetKeyCache;
  }

  /**
   * Updates or deletes the DOI metadata in DataCite. This can happen if dataset metadata has changed but also if a release was added or removed.
   * In case an entire project gets deleted
   * which removed the sources already from the DB and cascades a project deletion to all its releases!!!
   */
  @Subscribe
  public void update(DoiChange event){
    if (event.getDoi().isCOL()) {
      int datasetKey;
      Integer sourceKey = null;
      try {
        // a dataset/release DOI
        datasetKey = event.getDoi().datasetKey();
        // make sure we have a project release
        var info = DatasetInfoCache.CACHE.info(datasetKey);
        if (info.origin != DatasetOrigin.RELEASED || info.sourceKey == null) {
          LOG.warn("COL dataset DOI {} that is not a release: {}", event.getDoi(), datasetKey);
          return;
        }
      } catch (IllegalArgumentException e) {
        // a source dataset DOI
        var key = event.getDoi().sourceDatasetKey();
        datasetKey = key.getDatasetKey();
        sourceKey = key.getId();
        var project = DatasetInfoCache.CACHE.info(sourceKey);
        var source = DatasetInfoCache.CACHE.info(sourceKey);
        if (project.origin != DatasetOrigin.MANAGED || project.key != source.sourceKey) {
          LOG.warn("COL source dataset DOI {} that is not a source dataset key {}", event.getDoi(), sourceKey);
          return;
        }
      }

      if (event.isDelete()) {
        if (sourceKey == null) {
          delete(event.getDoi(), datasetKey);
        } else {
          delete(event.getDoi(), datasetKey, sourceKey);
        }

      } else if (!deleted.contains(event.getDoi())){
        if (sourceKey == null) {
          update(event.getDoi(), datasetKey);
        } else {
          update(event.getDoi(), datasetKey, sourceKey);
        }
      }
    }
  }

  private void update(DOI doi, int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      Dataset d = session.getMapper(DatasetMapper.class).get(datasetKey);
      d.setDoi(doi); // make sure we don't accidently update some other DOI
      boolean latest = datasetKeyCache.isLatestRelease(datasetKey);
      var attr = converter.release(d, latest);
      update(attr);
    }
  }

  private void update(DOI doi, int datasetKey, int sourceDatasetKey) {
    try (SqlSession session = factory.openSession()) {
      Dataset project = session.getMapper(DatasetMapper.class).get(datasetKey);
      var source = session.getMapper(ProjectSourceMapper.class).getProjectSource(sourceDatasetKey, datasetKey);
      source.setDoi(doi); // make sure we dont accidently update some other DOI
      boolean latest = datasetKeyCache.isLatestRelease(datasetKey);
      var attr = converter.source(source, project, latest);
      update(attr);
    }
  }

  private void update(DoiAttributes attr) {
    try {
      doiService.update(attr);
    } catch (DoiException e) {
      LOG.error("Error updating COL DOI {}", attr.getDoi(), e);
    }
  }

  private void delete(DOI doi, int datasetKey){
    delete(doi, converter.datasetURI(datasetKey, false));
  }

  private void delete(DOI doi, int datasetKey, int sourceDatasetKey){
    delete(doi, converter.sourceURI(datasetKey, sourceDatasetKey, false));
  }

  private void delete(DOI doi, URI url){
    try {
      // if the release was still private, it only had a draft DOI which gets removed completely
      if (!doiService.delete(doi)) {
        // DOI was hidden only - make sure the URL is correct and points to CLB
        doiService.update(doi, url);
      }
      deleted.add(doi);
      // sources might also have a DOI which we need to remove or update depending on whether the DOI is shared between releases.
      // This is managed by triggering a DoiUpdate event for each of the DOIs in the DatasetDAO
    } catch (DoiException e) {
      LOG.error("Error deleting COL DOI {}", doi, e);
    }
  }

}
