package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A job that updates all latest dataset metrics stored both on the filesystem and postgres.
 * For each dataset all metrics are generated and applied to the current, latest attempt.
 *
 * For releases the metrics are stored as part of the project, so all older release attempts
 * will be changed.
 */
public class MetricsUpdater implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsUpdater.class);

  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;
  private Integer datasetKey;
  private int counter;
  private int sCounter;
  private int sCounterFailed;
  private DatasetImportDao diDao;
  private SectorImportDao siDao;
  private Set<SectorAttempt> metricsDone = new HashSet<>();

  public MetricsUpdater(SqlSessionFactory factory, WsServerConfig cfg, Integer datasetKey) {
    this.factory = factory;
    this.cfg = cfg;
    this.datasetKey = datasetKey;
  }

  @Override
  public void run() {
    diDao = new DatasetImportDao(factory, cfg.metricsRepo);
    siDao = new SectorImportDao(factory, cfg.metricsRepo);
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      if (datasetKey != null){
        updateDataset(dm.get(datasetKey));
      } else {
        LOG.info("Start file metrics update for all datasets");
        dm.process(null).forEach(this::updateDataset);
      }
    }
    LOG.info("Finished file metrics update updating {} datasets", counter);
  }

  private void updateDataset(Dataset d) {
    final boolean isRelease = DatasetOrigin.RELEASED == d.getOrigin();
    // the datasetKey to store metrics under - the project in case of a release
    int datasetKey = isRelease ? d.getSourceKey() : d.getKey();
    if (d.getImportAttempt() != null) {
      int attempt = d.getImportAttempt();
      DatasetImport di = diDao.getAttempt(datasetKey, attempt);
      if (di == null) {
        LOG.warn("No import metrics exist for dataset {} attempt {}, but which was given in dataset {}", datasetKey, attempt, d.getKey());
      } else {
        LOG.info("Build import metrics for dataset " + d.getKey());
        diDao.updateMetrics(di, d.getKey());
        diDao.update(di);
      }
    } else {
      LOG.info("No import existing for dataset {}", d.getKey());
    }

    // SECTORS
    // managed & released datasets can have sectors
    try (SqlSession session = factory.openSession()) {
      sCounter = 0;
      sCounterFailed = 0;
      for (Sector s : session.getMapper(SectorMapper.class).processDataset(d.getKey())){
        updateSector(s, datasetKey);
      }
      if (sCounter > 0) {
        LOG.info("Updated metrics for {} sectors from dataset {}, {} failed", sCounter, d.getKey(), sCounterFailed);
      }
    } catch (Exception e) {
      LOG.error("Failed to update sector metrics for dataset {}", d.getKey(), e);
    }
    counter++;

    // wipe all metrics on the filesystem for releases - we stored things there in the early days
    if (isRelease) {
      // subdir includes also sectors
      File dir = diDao.getFileMetricsDao().subdir(d.getKey());
      if (dir.exists()) {
        try {
          FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
          LOG.warn("Failed to remove metrics directory {} for release {}", dir, d.getKey());
        }
      }
    }
  }

  static class SectorAttempt {
    public final int datasetKey;
    public final int sectorKey;
    public final int attempt;

    SectorAttempt(int datasetKey, int sectorKey, int attempt) {
      this.datasetKey = datasetKey;
      this.sectorKey = sectorKey;
      this.attempt = attempt;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SectorAttempt)) return false;
      SectorAttempt that = (SectorAttempt) o;
      return datasetKey == that.datasetKey &&
        sectorKey == that.sectorKey &&
        attempt == that.attempt;
    }

    @Override
    public int hashCode() {
      return Objects.hash(datasetKey, sectorKey, attempt);
    }
  }

  private void updateSector(Sector s, int projectKey) {
    // release sector syncs are stored in the project
    if (s.getSyncAttempt() != null) {
      SectorAttempt sa = new SectorAttempt(s.getDatasetKey(), s.getId(), s.getSyncAttempt());
      if (!metricsDone.contains(sa)) {
        metricsDone.add(sa);
        sCounter++;
        DSID<Integer> metricsKey = DSID.of(projectKey, s.getId());
        SectorImport si = siDao.getAttempt(metricsKey, sa.attempt);
        if (si == null) {
          LOG.warn("No import metrics exist for sector {} attempt {}, but which was given in sector {}", metricsKey, sa.attempt, s.getKey());
          sCounterFailed++;
        } else {
          LOG.info("Build import metrics for sector " + s.getKey());
          siDao.updateMetrics(si, s.getDatasetKey());
        }
      }
    }
  }
}
