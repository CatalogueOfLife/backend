package life.catalogue.es;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.NameUsageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

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
   * Removes a given root taxon and all its descendants (taxa & synonyms) from ElasticSearch.
   */
  void deleteSubtree(DSID<String> root);

  /**
   * Indexes an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  void indexDataset(int datasetKey);

  /**
   * Removes an entire dataset from ElasticSearch.
   * @return number of deleted docs
   */
  int deleteDataset(int datasetKey);

  /**
   * Recreates a new search index from scratch
   * and re-indexes all datasets.
   */
  void indexAll();

  /**
   * Removes a single usage document from ES
   * @param usageId
   */
  void delete(DSID<String> usageId);

  /**
   * Updates Elasticsearch for the provided usage ids.
   * This does not work for bare names!
   * 
   * @param datasetKey
   * @param taxonIds
   */
  void update(int datasetKey, Collection<String> taxonIds);

  /**
   * Adds given usages incl bare names to the index without deleting them beforehand.
   * @return number of successfully added documents
   */
  int add(List<NameUsageWrapper> usages);

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
      public void deleteSubtree(DSID<String> root) {
        LOG.info("No Elastic Search configured, pass through deletion of subtree starting with taxon {}", root);
      }

      @Override
      public void indexDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
      }

      @Override
      public int deleteDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through deletion of dataset {}", datasetKey);
        return 0;
      }

      @Override
      public void update(int datasetKey, Collection<String> taxonIds) {
        LOG.info("No Elastic Search configured. Passing through taxa {}", taxonIds);
      }

      @Override
      public int add(List<NameUsageWrapper> usages) {
        LOG.info("No Elastic Search configured, pass through adding of {} usages", usages.size());
        return 0;
      }

      @Override
      public void indexAll() {
        LOG.info("No Elastic Search configured. Passing through");
      }

      @Override
      public void delete(DSID<String> usageId) {
        LOG.info("No Elastic Search configured, pass through deleting of usage {}", usageId);
      }

      @Override
      public void updateClassification(int datasetKey, String rootTaxonId) {
        LOG.info("No Elastic Search configured. Passing through");
      }

    };
  }
}