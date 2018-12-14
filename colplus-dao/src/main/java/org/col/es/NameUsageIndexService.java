package org.col.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageIndexService {
  
  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  void indexDataset(int datasetKey);
  
  /**
   * @return a pass through indexing service that does not do anything. Good for tests
   */
  static NameUsageIndexService passThru() {
    return datasetKey -> LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
  }
}
