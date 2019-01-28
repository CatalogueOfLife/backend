package org.col.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageIndexService {

  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  void indexDataset(int datasetKey);

  void indexAll();

  /**
   * @return a pass through indexing service that does not do anything. Good for tests
   */
  static NameUsageIndexService passThru() {
    return new NameUsageIndexService() {

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
