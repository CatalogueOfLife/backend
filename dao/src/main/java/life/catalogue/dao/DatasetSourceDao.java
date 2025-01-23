package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.db.mapper.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static life.catalogue.api.vocab.DatasetOrigin.*;

public class DatasetSourceDao {
  private final static Logger LOG = LoggerFactory.getLogger(DatasetSourceDao.class);
  private final SqlSessionFactory factory;

  public DatasetSourceDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  /**
   * @param datasetKey the dataset key of the release or project
   * @param sourceDatasetKey the dataset key of the source within the release or project
   * @param dontPatch if true return the original project source metadata without the patch. This works only for managed datasets, not releases
   */
  public Dataset get(int datasetKey, int sourceDatasetKey, boolean dontPatch){
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(datasetKey);
    Dataset d;
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSourceMapper dsm = session.getMapper(DatasetSourceMapper.class);
      if (EXTERNAL == info.origin) {
        throw new IllegalArgumentException("Dataset "+datasetKey+" is external");

      } else if (PROJECT == info.origin) {
        d = dsm.getProjectSource(sourceDatasetKey, datasetKey);
        if (d != null && !dontPatch) {
          // get latest version with patch applied
          patch(d, datasetKey, session.getMapper(DatasetPatchMapper.class));
        }

      } else {
        d = dsm.getReleaseSource(sourceDatasetKey, datasetKey);
        // if the release was deleted, the source should also be marked as deleted
        if (info.deleted) {
          final Dataset release = dm.get(datasetKey);
          d.setDeleted(release.getDeleted());
        }
      }
    }
    return d;
  }

  public int update(int datasetKey, Dataset source, int user) {
    source.applyUser(user);

    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(datasetKey);
    if (!info.origin.isRelease()) {
      throw new IllegalArgumentException("source has to be from a release");
    }

    try (SqlSession session = factory.openSession()) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      if (psm.delete(source.getKey(), datasetKey) > 0) {
        psm.create(datasetKey, source);
        session.commit();
        return 1;
      }
    }
    return 0;
  }

  /**
   * Returns simple source datasets like the main list method but without
   * - description
   * - container dataset
   * - bibliography
   * - contributors
   *
   * @param datasetKey
   * @return
   */
  public List<Dataset> listSimple(int datasetKey, boolean inclPublisherSources){
    DatasetInfoCache.DatasetInfo info = DatasetInfoCache.CACHE.info(datasetKey).requireOrigin(RELEASE, XRELEASE, PROJECT);
    List<Dataset> sources;
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      final var container = dm.get(datasetKey);
      final var settings = dm.getSettings(info.keyOrProjectKey());
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      if (info.origin.isRelease()) {
        sources = psm.listReleaseSourcesSimple(datasetKey, inclPublisherSources);

      } else {
        sources = psm.listProjectSourcesSimple(datasetKey, inclPublisherSources);
        // a project, get latest version with patch applied
        final DatasetPatchMapper pm = session.getMapper(DatasetPatchMapper.class);
        sources.forEach(d -> patch(d, datasetKey, pm));
      }
      sources.forEach(d -> d.addContainer(container, settings));
    }
    return sources;
  }

  public List<DatasetSimple> suggest(int datasetKey, String query, boolean inclMergeSources){
    DatasetInfoCache.CACHE.info(datasetKey).requireOrigin(RELEASE, XRELEASE, PROJECT);
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      return dm.suggest(query, datasetKey, inclMergeSources);
    }
  }

  public List<Dataset> listReleaseSources(int datasetKey, boolean inclPublisherSources){
    DatasetInfoCache.CACHE.info(datasetKey).requireOrigin(RELEASE, XRELEASE);
    try (SqlSession session = factory.openSession()) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      return psm.listReleaseSources(datasetKey, inclPublisherSources);
    }
  }

  /**
   * Lists all project or release sources based on the sectors in the dataset,
   * retrieving metadata either from the latest version
   * or an archived copy depending on the import attempt of the last sync stored in the sectors.
   * It does NOT rely on dataset_source records for releases and can be used to create them.
   *
   * @param projectKey the dataset key of the project to load patches from
   * @param datasetKey project or release key to query for source sectors
   * @param inclPublisherSources if true includes all sources, if false excludes the sources which have a sector publisher
   */
  public List<Dataset> listSectorBasedSources(int projectKey, int datasetKey, boolean inclPublisherSources){
    try (SqlSession session = factory.openSession()) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      DatasetPatchMapper pm = session.getMapper(DatasetPatchMapper.class);
      // get latest version with patch applied
      List<Dataset> sources = psm.listProjectSources(datasetKey, inclPublisherSources);
      sources.forEach(d -> patch(d, projectKey, pm));
      return sources;
    }
  }

  /**
   * Loads and applies the projects metadata patch, if existing, to the dataset d
   * @param d dataset to be patched
   * @param projectKey dataset key to project NOT a release
   * @return the same dataset instance d as given
   */
  private Dataset patch(Dataset d, int projectKey, DatasetPatchMapper pm){
    Dataset patch = pm.get(projectKey, d.getKey());
    if (patch != null) {
      LOG.debug("Apply dataset patch from project {} to {}: {}", projectKey, d.getKey(), d.getTitle());
      d.applyPatch(patch);
    }
    return d;
  }

  public static class SourceMetrics extends ImportMetrics {
    private final int sourceKey;
    // current attempt of the imported dataset
    private Integer latestAttempt;
    private Integer latestUsagesCount;
    private final Set<Integer> datasetAttempt = new TreeSet<>();

    public SourceMetrics(int datasetKey, int sourceKey) {
      setDatasetKey(datasetKey);
      this.sourceKey = sourceKey;
    }

    public int getSourceKey() {
      return sourceKey;
    }

    public Integer getLatestAttempt() {
      return latestAttempt;
    }

    public void setLatestAttempt(Integer latestAttempt) {
      this.latestAttempt = latestAttempt;
    }

    public Integer getLatestUsagesCount() {
      return latestUsagesCount;
    }

    public void setLatestUsagesCount(Integer latestUsagesCount) {
      this.latestUsagesCount = latestUsagesCount;
    }

    public Set<Integer> getDatasetAttempt() {
      return datasetAttempt;
    }

    @Override
    @JsonIgnore // this is not used for aggregated metrics as we have a range!
    public int getAttempt() {
      return super.getAttempt();
    }
  }

  public ImportMetrics releaseMetrics(int datasetKey, @Nullable Sector.Mode mode, @Nullable UUID publisher) {
    final var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (!info.origin.isRelease()) {
      throw new IllegalArgumentException("dataset has to be a release");
    }

    // aggregate metrics based on sector syncs/imports
    ImportMetrics m = new ImportMetrics();
    m.setDatasetKey(datasetKey);
    try (SqlSession session = factory.openSession()) {
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      AtomicInteger sectorCounter = new AtomicInteger(0);
      Integer projectKey = info.sourceKey;
      for (Sector s : session.getMapper(SectorMapper.class).listByDataset(datasetKey, null, null)){
        if (s.getSyncAttempt() != null) {
          if (publisher != null) {
            var src = DatasetInfoCache.CACHE.info(s.getSubjectDatasetKey());
            if (!Objects.equals(publisher, src.publisherKey)) {
              continue;
            }
          }
          if (mode != null && s.getMode() != mode) continue;
          // matches. Add!
          SectorImport si = sim.get(DSID.of(projectKey, s.getId()), s.getSyncAttempt());
          m.add(si);
          sectorCounter.incrementAndGet();
        }
      }
      m.setSectorCount(sectorCounter.get());
    }
    return m;
  }

  /**
   * Retrieve the metrics for a single source of a project or release
   * @param datasetKey of the project or release
   * @param sourceKey dataset key of the source in the project/release
   * @return
   */
  public SourceMetrics sourceMetrics(int datasetKey, int sourceKey) {
    SourceMetrics metrics = new SourceMetrics(datasetKey, sourceKey);

    try (SqlSession session = factory.openSession()) {
      // could throw not found
      var info = DatasetInfoCache.CACHE.info(datasetKey);

      // get current source in CLB
      var source = session.getMapper(DatasetMapper.class).getOrThrow(sourceKey, Dataset.class);
      metrics.setLatestAttempt(source.getAttempt());
      metrics.setLatestUsagesCount(source.getSize());

      // aggregate metrics based on sector syncs/imports
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      AtomicInteger sectorCounter = new AtomicInteger(0);
      // a release? use mother project in that case
      if (info.origin.isRelease()) {
        Integer projectKey = info.sourceKey;
        for (Sector s : session.getMapper(SectorMapper.class).listByDataset(datasetKey, sourceKey, null)){
          if (s.getSyncAttempt() != null) {
            SectorImport m = sim.get(DSID.of(projectKey, s.getId()), s.getSyncAttempt());
            add(metrics, m);
            sectorCounter.incrementAndGet();
          }
        }
      } else {
        for (SectorImport m : sim.list(null, datasetKey, sourceKey, null, true, null)) {
          add(metrics, m);
          sectorCounter.incrementAndGet();
        }
      }
      metrics.setSectorCount(sectorCounter.get());
      return metrics;
    }
  }

  private void add(SourceMetrics m, SectorImport si) {
    if (si != null) {
      m.add(si);
      // track attempts & md5
      if (si.getDatasetAttempt() != null) {
        m.datasetAttempt.add(si.getDatasetAttempt());
      }
    }
  }

}
