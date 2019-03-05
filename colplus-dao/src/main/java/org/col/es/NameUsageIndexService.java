package org.col.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageIndexService {

  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);
  
  void indexSector(int sectorKey);

  void indexDataset(int datasetKey);

  void indexAll();

  /**
   * @return a pass through indexing service that does not do anything. Good for tests
   */
  static NameUsageIndexService passThru() {
    return new NameUsageIndexService() {
  
      @Override
      public void indexSector(int sectorKey) {
        LOG.info("No Elastic Search configured, pass through sector {}", sectorKey);
      }

      @Override
      public void indexDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
      }

      @Override
      public void indexAll() {
       LOG.info("No Elastic Search configured. Passing through");
      }
    };
  }
}
