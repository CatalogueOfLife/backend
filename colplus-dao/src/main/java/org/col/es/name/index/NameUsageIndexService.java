package org.col.es.name.index;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageIndexService {

  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  /**
   * Indexes all CoL usages from an entire sector from postgres into ElasticSearch using the bulk API.
   */
  void indexSector(int sectorKey);

  /**
   * Removed all CoL usage docs of the given sector from ElasticSearch, i.e. taxa and synonyms.
   */
  void deleteSector(int sectorKey);

  /**
   * Indexes an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  void indexDataset(int datasetKey);

  /**
   * Re-indexes all datasets from scratch
   */
  void indexAll();

  /**
   * Indexes given taxa from the same dataset from postgres into ElasticSearch. Only use this method if you are <i>sure</i> the provided ids
   * don't exist yet in the index, otherwise you will create functionally duplicate documents. Use {@link #sync(int, Collection) sync} to
   * sync Elasticsearch to Postgres for the provided taxon ids.
   */
  void indexTaxa(int datasetKey, Collection<String> taxonIds);

  /**
   * Performs a sync between Postgres and Elasticsearch for the provided taxon ids.
   * 
   * @param datasetKey
   * @param taxonIds
   */
  void sync(int datasetKey, Collection<String> taxonIds);

  /**
   * Updates the classification for all descendants in the subtree identified by the rootTaxonId. All other information is left as is and no
   * new docs are generated, i.e. all taxa must have been indexed before.
   */
  void updateClassification(int datasetKey, String rootTaxonId);

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
      public void deleteSector(int sectorKey) {
        LOG.info("No Elastic Search configured, pass through deletion of sector {}", sectorKey);
      }

      @Override
      public void indexDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
      }

      /**
       * Indexes all CoL usages from an entire sector from postgres into ElasticSearch using the bulk API.
       */
      @Override
      public void indexTaxa(int datasetKey, Collection<String> taxonIds) {
        LOG.info("No Elastic Search configured. Passing through taxa {}", taxonIds);
      }

      @Override
      public void sync(int datasetKey, Collection<String> taxonIds) {
        LOG.info("No Elastic Search configured. Passing through taxa {}", taxonIds);
      }

      @Override
      public void indexAll() {
        LOG.info("No Elastic Search configured. Passing through");
      }

      @Override
      public void updateClassification(int datasetKey, String rootTaxonId) {
        LOG.info("No Elastic Search configured, pass through taxon {}", rootTaxonId);
      }

    };
  }
}
