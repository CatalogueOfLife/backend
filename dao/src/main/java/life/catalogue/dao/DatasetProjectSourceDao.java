package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.db.mapper.*;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static life.catalogue.api.vocab.DatasetOrigin.MANAGED;
import static life.catalogue.api.vocab.DatasetOrigin.RELEASED;

public class DatasetProjectSourceDao {
  private final static Logger LOG = LoggerFactory.getLogger(DatasetProjectSourceDao.class);
  private final SqlSessionFactory factory;

  public DatasetProjectSourceDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  /**
   * @param datasetKey the dataset key of the release or managed project
   * @param sourceDatasetKey the dataset key of the source within the release or project
   * @param dontPatch if true return the original project source metadata without the patch. This works only for managed datasets, not releases
   */
  public ArchivedDataset get(int datasetKey, int sourceDatasetKey, boolean dontPatch){
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(datasetKey);
    ArchivedDataset d;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      if (MANAGED == info.origin) {
        d = psm.getProjectSource(sourceDatasetKey, datasetKey);
        if (d != null && !dontPatch) {
          // get latest version with patch applied
          final DatasetSettings settings = dm.getSettings(datasetKey);
          final Dataset project = dm.get(datasetKey);
          patch(d, datasetKey, project, session.getMapper(DatasetPatchMapper.class), settings);
        }

      } else if (RELEASED == info.origin) {
        d = psm.getReleaseSource(sourceDatasetKey, datasetKey);
        // if the release was deleted, the source should also be marked as deleted
        if (info.deleted) {
          final Dataset release = dm.get(datasetKey);
          d.setDeleted(release.getDeleted());
        }

      } else {
        throw new IllegalArgumentException("Dataset "+datasetKey+" is not a project");
      }
    }
    return d;
  }

  /**
   * List the source datasets for a project or release.
   * If the key points to a release the patched and archived source metadata is returned.
   * If it points to a live project, the metadata is taken from the dataset archive at the time of the last successful sync attempt
   * and then patched.
   * @param datasetKey project or release key
   * @param projectForPatching optional dataset used for building the source citations, if null master project is used
   * @param rebuild if true force to rebuild source metadata and not take it from the source archive
   */
  public List<ArchivedDataset> list(int datasetKey, @Nullable Dataset projectForPatching, boolean rebuild){
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(datasetKey).requireOrigin(RELEASED, MANAGED);
    List<ArchivedDataset> sources;
    try (SqlSession session = factory.openSession()) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      if (RELEASED == info.origin && !rebuild) {
        sources = psm.listReleaseSources(datasetKey);

      } else {
        // get latest version with patch applied
        final int projectKey = RELEASED == info.origin ? info.sourceKey : datasetKey;
        final DatasetSettings settings = session.getMapper(DatasetMapper.class).getSettings(projectKey);
        if (settings != null && settings.has(Setting.RELEASE_SOURCE_CITATION_TEMPLATE)) {
          LOG.debug("Use source citation template >>>{}<<< from project {}", settings.getString(Setting.RELEASE_SOURCE_CITATION_TEMPLATE), projectKey);
        } else {
          LOG.warn("No source citation template configured from project {}", projectKey);
        }

        final Dataset project = projectForPatching != null ? projectForPatching : session.getMapper(DatasetMapper.class).get(datasetKey);
        DatasetPatchMapper pm = session.getMapper(DatasetPatchMapper.class);

        sources = psm.listProjectSources(datasetKey);
        sources.forEach(d -> patch(d, projectKey, project, pm, settings));
      }
    }
    return sources;
  }

  /**
   * Applies the projects dataset patch if existing to the dataset d
   * @param d dataset to be patched
   * @param settings project settings
   * @return the same dataset instance d as given
   */
  private ArchivedDataset patch(ArchivedDataset d, int projectKey, Dataset patchProject, DatasetPatchMapper pm, DatasetSettings settings){
    ArchivedDataset patch = pm.get(projectKey, d.getKey());
    if (patch != null) {
      LOG.info("Apply dataset patch from project {} to {}: {}", patchProject.getKey(), d.getKey(), d.getTitle());
      d.applyPatch(patch);
    }
    // build an in project citation?
    if (settings != null && settings.has(Setting.RELEASE_SOURCE_CITATION_TEMPLATE)) {
      try {
        String citation = CitationUtils.fromTemplate(d, patchProject, settings.getString(Setting.RELEASE_SOURCE_CITATION_TEMPLATE)).trim();
        d.setCitation(citation);
      } catch (IllegalArgumentException e) {
        LOG.warn("Failed to create citation for source dataset {}", d.getKey(), e);
      }
    }
    return d;
  }

  static class SourceMetrics extends ImportMetrics {
    // current attempt of the imported dataset
    private Integer latestAttempt;
    private Integer latestUsageCount;

    public Integer getLatestAttempt() {
      return latestAttempt;
    }

    public void setLatestAttempt(Integer latestAttempt) {
      this.latestAttempt = latestAttempt;
    }

    public Integer getLatestUsageCount() {
      return latestUsageCount;
    }

    public void setLatestUsageCount(Integer latestUsageCount) {
      this.latestUsageCount = latestUsageCount;
    }
  }

  public ImportMetrics projectSourceMetrics(int datasetKey, int sourceKey) {
    SourceMetrics metrics = new SourceMetrics();
    metrics.setAttempt(-1);
    metrics.setDatasetKey(datasetKey);

    try (SqlSession session = factory.openSession()) {
      // could throw not found
      var info = DatasetInfoCache.CACHE.info(datasetKey);

      // get current source in CLB
      var source = session.getMapper(DatasetMapper.class).getOrThrow(sourceKey, Dataset.class);
      metrics.setLatestAttempt(source.getImportAttempt());
      metrics.setLatestUsageCount(source.getSize());

      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      AtomicInteger sectorCounter = new AtomicInteger(0);
      // a release? use mother project in that case
      if (info.origin == DatasetOrigin.RELEASED) {
        Integer projectKey = info.sourceKey;
        for (Sector s : session.getMapper(SectorMapper.class).listByDataset(datasetKey, sourceKey)){
          if (s.getSyncAttempt() != null) {
            SectorImport m = sim.get(DSID.of(projectKey, s.getId()), s.getSyncAttempt());
            metrics.add(m);
            sectorCounter.incrementAndGet();
          }
        }
      } else {
        for (SectorImport m : sim.list(null, datasetKey, sourceKey, null, true, null)) {
          metrics.add(m);
          sectorCounter.incrementAndGet();
        }
      }
      metrics.setSectorCount(sectorCounter.get());
      return metrics;
    }
  }
}
