package life.catalogue.doi;

import life.catalogue.api.event.ChangeDoi;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.event.DoiListener;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiException;
import life.catalogue.doi.service.DoiService;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to update or delete DOI metadata when datasets change or get deleted.
 * DOIs for releases and projects are 1:1, so its simple to delete or update them.
 *
 * TODO: We might need to rethink the logic for annual releases
 */
public class DoiUpdater implements DoiListener, DatasetListener {
  private static final Logger LOG = LoggerFactory.getLogger(DoiUpdater.class);
  private final SqlSessionFactory factory;
  private final DoiService doiService;
  private final DatasetConverter converter;
  private final Set<DOI> deleted = ConcurrentHashMap.newKeySet();
  private final LatestDatasetKeyCache datasetKeyCache;

  public DoiUpdater(SqlSessionFactory factory, DoiService doiService, LatestDatasetKeyCache datasetKeyCache, DatasetConverter converter) {
    this.factory = factory;
    this.doiService = doiService;
    this.converter = converter;
    this.datasetKeyCache = datasetKeyCache;
  }

  /**
   * Updates or deletes the DOI metadata in DataCite. This can happen if dataset metadata has changed but also if a release was added or removed.
   * In case an entire project gets deleted
   * which removed the sources already from the DB and cascades a project deletion to all its releases!!!
   */
  @Override
  public void doiChanged(ChangeDoi event){
    if (event.getDoi().isCOL()) {
      try {
        if (event.getDoi().isDatasetAttempt()) {
          // a dataset attempt DOI
          var key = event.getDoi().datasetAttemptKey();
          int datasetKey = key.getDatasetKey();
          int attempt = key.getId();
          var dataset = DatasetInfoCache.CACHE.info(datasetKey, true);
          if (dataset.origin.isProjectOrRelease()) {
            LOG.warn("COL dataset attempt DOI {} that refers to {} dataset {}", event.getDoi(), dataset.origin, datasetKey);
          } else if (event.isDelete()) {
            deleteAttempt(event.getDoi(), datasetKey, attempt);
          } else if (!deleted.contains(event.getDoi())){
            updateAttempt(event.getDoi(), datasetKey, attempt);
          }

        } else if (event.getDoi().isDatasetSource()) {
          // a source dataset DOI
          var key = event.getDoi().sourceDatasetKey();
          int releaseKey = key.getDatasetKey();
          int sourceKey = key.getId();
          var release = DatasetInfoCache.CACHE.info(releaseKey, true);
          if (!release.origin.isRelease()) {
            LOG.warn("COL source dataset DOI {} that is not linked to a release key {}", event.getDoi(), releaseKey);
            return;
          }
          if (event.isDelete()) {
            deleteSource(event.getDoi(), releaseKey, sourceKey);
          } else if (!deleted.contains(event.getDoi())){
            updateSource(event.getDoi(), releaseKey, sourceKey);
          }

        } else {
          // a dataset/release DOI
          int datasetKey = event.getDoi().datasetKey();
          if (event.isDelete()) {
            delete(event.getDoi(), datasetKey);
          } else if (!deleted.contains(event.getDoi())){
            update(event.getDoi(), datasetKey);
          }
        }

      } catch (Exception e) {
        LOG.error("Error processing ChangeDoi event for COL dataset DOI {}", event.getDoi(), e);
      }
    }
  }

  @Override
  public void datasetChanged(DatasetChanged d) {
    if (d.isCreated() && d.obj.getDoi() != null) {
      // dataset dao does not deal with DOIs, we need to create them in DataCite here!
      var attr = buildDatasetMetadata(d.obj);
      create(attr);
    }
  }

  /**
   * Updates a release
   */
  private void update(DOI doi, int datasetKey) {
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(datasetKey);
      d.setDoi(doi); // make sure we don't accidently update some other DOI
      DoiAttributes attr;
      if (d.getOrigin().isRelease()) {
        boolean latest = datasetKeyCache.isLatestRelease(datasetKey);
        final Integer prevReleaseKey = dm.previousRelease(datasetKey);
        attr = buildReleaseMetadata(d.getSourceKey(), latest, d, prevReleaseKey);

      } else {
        attr = buildDatasetMetadata(d);
      }
      update(attr);
    }
  }

  /**
   * Updates a release source
   */
  private void updateSource(DOI doi, int datasetKey, int sourceDatasetKey) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset release = dm.get(datasetKey);
      var source = session.getMapper(DatasetSourceMapper.class).getProjectSource(sourceDatasetKey, datasetKey);
      source.setDoi(doi); // make sure we dont accidently update some other DOI
      boolean latest = datasetKeyCache.isLatestRelease(datasetKey);
      var attr = buildSourceMetadata(source, release, latest);
      update(attr);
    }
  }

  /**
   * Updates a release source
   */
  private void updateAttempt(DOI doi, int datasetKey, int attempt) {
    try (SqlSession session = factory.openSession()) {
      var dam = session.getMapper(DatasetArchiveMapper.class);
      var dim = session.getMapper(DatasetImportMapper.class);
      Dataset d = dam.get(datasetKey, attempt);
      d.setDoi(doi); // make sure we dont accidently update some other DOI
      DatasetImport lastImport = dim.getLast(datasetKey, attempt, ImportState.FINISHED);
      var attr = buildAttemptMetadata(d, lastImport == null ? null : lastImport.getAttempt());
      update(attr);
    }
  }

  private void create(DoiAttributes attr) {
    try {
      doiService.create(attr);
    } catch (DoiException e) {
      LOG.error("Error creating COL DOI {}", attr.getDoi(), e);
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
    delete(doi, converter.datasetURI(datasetKey));
  }

  private void deleteSource(DOI doi, int datasetKey, int sourceDatasetKey){
    delete(doi, converter.sourceURI(datasetKey, sourceDatasetKey, false));
  }

  private void deleteAttempt(DOI doi, int datasetKey, int attempt){
    delete(doi, converter.attemptURI(datasetKey, attempt));
  }

  private void delete(DOI doi, URI url){
    if (doi != null) {
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

  private DOI doi(Dataset d) {
    return d != null ? d.getDoi() : null;
  }

  public DoiAttributes buildAttemptMetadata(Dataset d, @Nullable Integer prevAttempt, @Nullable Integer nextAttempt) {
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var dam = session.getMapper(DatasetArchiveMapper.class);
      Dataset project = .get(datasetKey);
      Dataset prevRelease = null;
      if (prevReleaseKey != null) {
        prevRelease = session.getMapper(DatasetMapper.class).get(prevReleaseKey);
      }
      return converter.datasetAttempt(d, latest, doi(project), doi(prevRelease));
    }
  }

  public DoiAttributes buildDatasetMetadata(Dataset d) {
    try (SqlSession session = factory.openSession(true)) {
      Dataset project = session.getMapper(DatasetMapper.class).get(projectKey);
      Dataset prevRelease = null;
      if (prevReleaseKey != null) {
        prevRelease = session.getMapper(DatasetMapper.class).get(prevReleaseKey);
      }
      return converter.release(d, latest, doi(project), doi(prevRelease));
    }
  }

  public DoiAttributes buildReleaseMetadata(int projectKey, boolean latest, Dataset release, @Nullable Integer prevReleaseKey) {
    try (SqlSession session = factory.openSession(true)) {
      Dataset project = session.getMapper(DatasetMapper.class).get(projectKey);
      Dataset prevRelease = null;
      if (prevReleaseKey != null) {
        prevRelease = session.getMapper(DatasetMapper.class).get(prevReleaseKey);
      }
      return converter.release(release, latest, doi(project), doi(prevRelease));
    }
  }

  public DoiAttributes buildSourceMetadata(Dataset source, Dataset project, boolean latest) {
    try (SqlSession session = factory.openSession(true)) {
      Dataset originalSource = session.getMapper(DatasetMapper.class).get(source.getKey());
      return converter.source(source, originalSource.getDoi(), project, latest);
    }
  }

}
