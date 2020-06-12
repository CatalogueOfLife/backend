package life.catalogue.admin;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Sector;
import life.catalogue.dao.NamesTreeDao;
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
  private int counter;
  private int sCounter;
  NamesTreeDao treeDao;

  public MetricsUpdater(SqlSessionFactory factory, WsServerConfig cfg) {
    this.factory = factory;
    this.cfg = cfg;
  }

  @Override
  public void run() {
    treeDao = new NamesTreeDao(factory, cfg.metricsRepo);
    LOG.info("Start file metrics update for all datasets");
    try (SqlSession session = factory.openSession()) {
      session.getMapper(DatasetMapper.class).process(null).forEach(this::updateDataset);
    }
    LOG.info("Finished file metrics update updating {} datasets", counter);
  }

  private void updateDataset(Dataset d) {
    LOG.info("Update file metrics for dataset {}", d.getKey());
    if (d.getImportAttempt() != null) {
      int attempt = d.getImportAttempt();
      try {
        treeDao.updateDatasetTree(d.getKey(), attempt);
      } catch (IOException e) {
        LOG.error("Failed to update text tree for dataset {}", d.getKey(), e);
      }
      try {
        treeDao.updateDatasetNames(d.getKey(), attempt);
      } catch (Exception e) {
        LOG.error("Failed to update name metrics for dataset {}", d.getKey(), e);
      }
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
        treeDao.updateSectorTree(s.getId(), attempt);
      } catch (IOException e) {
        LOG.error("Failed to update text tree for sector {}", s.getId(), e);
      }

      try {
        treeDao.updateSectorNames(s.getId(), attempt);
      } catch (Exception e) {
        LOG.error("Failed to update name metrics for sector {}", s.getId(), e);
      }
      sCounter++;
    }
  }
}
