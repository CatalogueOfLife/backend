package life.catalogue.es.name.index;

import life.catalogue.api.model.Sector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public interface NameUsageIndexService {

  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  /**
   * Indexes all CoL usages from an entire sector from postgres into ElasticSearch using the bulk API.
   */
  void indexSector(Sector sector);

  /**
   * Removed all CoL usage docs of the given sector from ElasticSearch, i.e. taxa and synonyms.
   */
  void deleteSector(int sectorKey);

  /**
   * Indexes an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  void indexDataset(int datasetKey);

  /**
   * Removes an entire dataset from ElasticSearch.
   */
  void deleteDataset(int datasetKey);

  /**
   * Re-indexes all datasets from scratch
   */
  void indexAll();

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
      public void indexSector(Sector sector) {
        LOG.info("No Elastic Search configured, pass through sector {}", sector);
      }

      @Override
      public void deleteSector(int sectorKey) {
        LOG.info("No Elastic Search configured, pass through deletion of sector {}", sectorKey);
      }

      @Override
      public void indexDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
      }

      @Override
      public void deleteDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through deletion of dataset {}", datasetKey);
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
        LOG.info("No Elastic Search configured. Passing through");
      }

    };
  }
}