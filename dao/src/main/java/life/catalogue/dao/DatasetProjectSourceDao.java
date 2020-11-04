package life.catalogue.dao;

import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPatchMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;

public class DatasetProjectSourceDao {
  private final Logger LOG = LoggerFactory.getLogger(DatasetProjectSourceDao.class);
  private final SqlSessionFactory factory;

  public DatasetProjectSourceDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  /**
   * @param dontPatch if true return the original project source metadata without the patch. This works only for managed datasets, not releases
   */
  public ArchivedDataset get(int projectKey, int sourceDatasetKey, boolean dontPatch){
    DatasetOrigin origin = getProjectOrigin(projectKey);
    try (SqlSession session = factory.openSession()) {
      if (DatasetOrigin.MANAGED == origin) {
        ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
        ArchivedDataset d = psm.getProjectSource(sourceDatasetKey, projectKey);
        if (!dontPatch) {
          // get latest version with patch applied
          DatasetMapper dm = session.getMapper(DatasetMapper.class);
          final DatasetSettings settings = dm.getSettings(projectKey);
          final Dataset project = dm.get(projectKey);
          patch(d, projectKey, project, session.getMapper(DatasetPatchMapper.class), settings);
        }
        return d;

      } else if (DatasetOrigin.RELEASED == origin) {
        return session.getMapper(ProjectSourceMapper.class).getReleaseSource(sourceDatasetKey, projectKey);

      } else {
        throw new IllegalArgumentException("Dataset "+projectKey+" is not a project");
      }
    }
  }

  private DatasetOrigin getProjectOrigin(int projectKey){
    DatasetOrigin origin = DatasetInfoCache.CACHE.origin(projectKey);
    if (!origin.isProject()) {
      throw new IllegalArgumentException("Dataset "+projectKey+" is not a project");
    }
    return origin;
  }

  /**
   * List the source datasets for a project or release.
   * If the key points to a release the patched and archived source metadata is returned.
   * If it points to a live project, the metadata is taken from the dataset archive at the time of the last successful sync attempt
   * and then patched.
   * @param projectKey
   * @param projectForPatching optional dataset used for building the source citations, if null project is used
   */
  public List<ArchivedDataset> list(int projectKey, @Nullable Dataset projectForPatching){
    DatasetOrigin origin = getProjectOrigin(projectKey);

    List<ArchivedDataset> sources;
    try (SqlSession session = factory.openSession()) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      if (DatasetOrigin.RELEASED == origin) {
        sources = psm.listReleaseSources(projectKey);

      } else {
        // get latest version with patch applied
        final Dataset project = projectForPatching != null ? projectForPatching : session.getMapper(DatasetMapper.class).get(projectKey);
        final DatasetSettings settings = session.getMapper(DatasetMapper.class).getSettings(projectKey);
        DatasetPatchMapper pm = session.getMapper(DatasetPatchMapper.class);

        sources = psm.listProjectSources(projectKey);
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
    Dataset patch = pm.get(projectKey, d.getKey());
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
}
