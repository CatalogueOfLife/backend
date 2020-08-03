package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MetricsUpdater implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsUpdater.class);

  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;
  private Integer datasetKey;
  private int counter;
  private int sCounter;
  FileMetricsDatasetDao fmdDao;
  FileMetricsSectorDao fmsDao;

  public MetricsUpdater(SqlSessionFactory factory, WsServerConfig cfg, Integer datasetKey) {
    this.factory = factory;
    this.cfg = cfg;
    this.datasetKey = datasetKey;
  }

  @Override
  public void run() {
    fmdDao = new FileMetricsDatasetDao(factory, cfg.metricsRepo);
    fmsDao = new FileMetricsSectorDao(factory, cfg.metricsRepo);
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
      LOG.info("Update file metrics for dataset {}, attempt {}", d.getKey(), attempt);
      try {
        fmdDao.updateTree(d.getKey(), attempt);
      } catch (IOException e) {
        LOG.error("Failed to update text tree for dataset {}", d.getKey(), e);
      }
      try {
        fmdDao.updateNames(d.getKey(), attempt);
      } catch (Exception e) {
        LOG.error("Failed to update name metrics for dataset {}", d.getKey(), e);
      }
    } else {
      LOG.info("No import existing for dataset {}", d.getKey());
    }

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

  private void updateSector(Sector s) {
    // managed & released datasets can have sectors
    if (s.getSyncAttempt() != null) {
      int attempt = s.getSyncAttempt();
      try {
        fmsDao.updateTree(s, attempt);
      } catch (IOException e) {
        LOG.error("Failed to update text tree for sector {}", s.getId(), e);
      }

      try {
        fmsDao.updateNames(s, attempt);
      } catch (Exception e) {
        LOG.error("Failed to update name metrics for sector {}", s.getId(), e);
      }
      sCounter++;
    }
  }
}
