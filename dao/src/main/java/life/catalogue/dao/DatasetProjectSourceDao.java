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

import java.util.List;

public class DatasetProjectSourceDao {
  private final Logger LOG = LoggerFactory.getLogger(DatasetProjectSourceDao.class);
  private final SqlSessionFactory factory;

  public DatasetProjectSourceDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public ArchivedDataset get(int projectKey, int sourceDatasetKey){
    DatasetOrigin origin = getProjectOrigin(projectKey);
    try (SqlSession session = factory.openSession()) {
      if (DatasetOrigin.MANAGED == origin) {
        // get latest version with patch applied
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        final Dataset project = dm.get(projectKey);
        final DatasetSettings settings = dm.getSettings(projectKey);
        return patch(project, dm.get(sourceDatasetKey), session.getMapper(DatasetPatchMapper.class), settings);

      } else if (DatasetOrigin.RELEASED == origin) {
        return session.getMapper(ProjectSourceMapper.class).get(sourceDatasetKey, projectKey);

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
   */
  public List<ArchivedDataset> list(int projectKey){
    DatasetOrigin origin = getProjectOrigin(projectKey);

    List<ArchivedDataset> sources;
    try (SqlSession session = factory.openSession()) {
      ProjectSourceMapper psm = session.getMapper(ProjectSourceMapper.class);
      if (DatasetOrigin.RELEASED == origin) {
        sources = psm.listReleaseSources(projectKey);

      } else {
        // get latest version with patch applied
        final Dataset project = session.getMapper(DatasetMapper.class).get(projectKey);
        final DatasetSettings settings = session.getMapper(DatasetMapper.class).getSettings(projectKey);
        DatasetPatchMapper pm = session.getMapper(DatasetPatchMapper.class);

        sources = psm.listProjectSources(projectKey);
        sources.forEach(d -> patch(project, d, pm, settings));
      }
    }
    return sources;
  }

  private ArchivedDataset patch(Dataset project, ArchivedDataset d, DatasetPatchMapper pm, DatasetSettings settings){
    Dataset patch = pm.get(project.getKey(), d.getKey());
    if (patch != null) {
      LOG.info("Apply dataset patch from project {} to {}: {}", project.getKey(), d.getKey(), d.getTitle());
      d.apply(patch);
    }
    // build an in project citation?
    if (settings != null && settings.has(Setting.RELEASE_SOURCE_CITATION_TEMPLATE)) {
      try {
        String citation = CitationUtils.fromTemplate(project, d, settings.getString(Setting.RELEASE_SOURCE_CITATION_TEMPLATE));
        d.setCitation(citation);
      } catch (IllegalArgumentException e) {
        LOG.warn("Failed to create citation for source dataset {}", d.getKey(), e);
      }
    }
    return d;
  }
}
