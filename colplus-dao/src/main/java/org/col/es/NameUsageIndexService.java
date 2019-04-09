package org.col.es;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageIndexService {

  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  /**
   * Indexes all CoL usages from an entire sector from postgres into ElasticSearch using the bulk API.
   */
  void indexSector(Integer sectorKey);

  /**
   * Indexes an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  void indexDataset(Integer datasetKey);

  /**
   * Re-indexes all datasets from scratch
   */
  void indexAll();

  /**
   * Indexes given taxa from the same dataset from postgres into ElasticSearch.
   */
  void indexTaxa(Integer datasetKey, Collection<String> taxonIds);

  /**
   * @return a pass through indexing service that does not do anything. Good for tests
   */
  static NameUsageIndexService passThru() {
    return new NameUsageIndexService() {

      @Override
      public void indexSector(Integer sectorKey) {
        LOG.info("No Elastic Search configured, pass through sector {}", sectorKey);
      }

      @Override
      public void indexDataset(Integer datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
      }

      /**
       * Indexes all CoL usages from an entire sector from postgres into ElasticSearch using the bulk API.
       */
      @Override
      public void indexTaxa(Integer datasetKey, Collection<String> taxonIds) {
        LOG.info("No Elastic Search configured. Passing through taxa {}", taxonIds);
      }

      @Override
      public void indexAll() {
        LOG.info("No Elastic Search configured. Passing through");
      }
    };
  }
}
