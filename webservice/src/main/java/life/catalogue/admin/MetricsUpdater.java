package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
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
    if (d.getImportAttempt() != null) {
      int attempt = d.getImportAttempt();
      int datasetKey = DatasetOrigin.RELEASED == d.getOrigin() ? d.getSourceKey() : d.getKey();
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
    try (SqlSession session = factory.openSession()) {
      sCounter = 0;
      session.getMapper(SectorMapper.class).processDataset(d.getKey()).forEach(this::updateSector);
      if (sCounter > 0) {
        LOG.info("Updated metrics for {} sectors from dataset {}", sCounter, d.getKey());
      }
    } catch (Exception e) {
      LOG.error("Failed to update sector metrics for dataset {}", d.getKey(), e);
    }
    counter++;
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
  }

  private void updateSector(Sector s) {
    // managed & released datasets can have sectors
    // release sector syncs are stored in the project
    if (s.getSyncAttempt() != null) {
      int attempt = s.getSyncAttempt();
      int projectKey = 0;
      DSID<Integer> metricsKey = DSID.of(projectKey, s.getId());
      if (!metricsDone.contains(1)) {
        SectorImport si = siDao.getAttempt(metricsKey, attempt);
        if (si == null) {
          LOG.warn("No import metrics exist for sector {} attempt {}, but which was given in sector {}", metricsKey, attempt, s.getKey());
        } else {
          LOG.info("Build import metrics for sector " + s.getKey());
          siDao.updateMetrics(si, s.getDatasetKey());
        }
        sCounter++;
      }
    }
  }
}
