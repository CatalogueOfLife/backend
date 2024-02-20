package life.catalogue.es;

import life.catalogue.api.model.DSID;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.common.func.BatchConsumer;
import life.catalogue.es.nu.NameUsageWrapperConverter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameUsageIndexService {

  Logger LOG = LoggerFactory.getLogger(NameUsageIndexService.class);

  class Stats {
    public int usages;
    public int names;

    public int total() {
      return usages + names;
    }

    public void add(Stats other) {
      usages += other.usages;
      names += other.names;
    }
  }

  /**
   * Creates an empty name usage index, dropping any potentially existing index under the same name.
   */
  int createEmptyIndex();

  /**
   * Indexes all CoL usages from an entire sector from postgres into ElasticSearch using the bulk API.
   */
  Stats indexSector(DSID<Integer> sectorKey);

  /**
   * Removed all CoL usage docs of the given sector from ElasticSearch, i.e. taxa and synonyms.
   */
  void deleteSector(DSID<Integer> sectorKey);

  /**
   * Remove all bare name documents for the given dataset from the index.
   * @return number of removed bare names
   */
  int deleteBareNames(int datasetKey);

  /**
   * Removes a given root taxon and all its descendants (taxa & synonyms) from ElasticSearch.
   * @param keepRoot if true only deletes all descendants but keeps the root taxon
   */
  void deleteSubtree(DSID<String> root, boolean keepRoot);

  /**
   * Indexes an entire dataset from postgres into ElasticSearch using the bulk API.
   */
  Stats indexDataset(int datasetKey);

  BatchConsumer<NameUsageWrapper> buildDatasetIndexingHandler(int datasetKey);

  /**
   * Removes an entire dataset from ElasticSearch.
   * @return number of deleted docs
   */
  int deleteDataset(int datasetKey);

  /**
   * Recreates a new search index from scratch
   * and re-indexes all datasets.
   */
  Stats indexAll(int ... excludedDatasetKeys);

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
      public int createEmptyIndex() {
        LOG.info("No Elastic Search configured, pass through index deletion");
        return 204;
      }

      @Override
      public Stats indexSector(DSID<Integer> sectorKey) {
        LOG.info("No Elastic Search configured, pass through sector {}", sectorKey);
        return new Stats();
      }

      @Override
      public void deleteSector(DSID<Integer> sectorKey) {
        LOG.info("No Elastic Search configured, pass through deletion of sector {}", sectorKey);
      }

      @Override
      public void deleteSubtree(DSID<String> root, boolean keepRoot) {
        LOG.info("No Elastic Search configured, pass through deletion of subtree starting with taxon {}", root);
      }

      @Override
      public int deleteBareNames(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
        return 0;
      }

      @Override
      public Stats indexDataset(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
        return new Stats();
      }

      @Override
      public BatchConsumer<NameUsageWrapper> buildDatasetIndexingHandler(int datasetKey) {
        LOG.info("No Elastic Search configured, pass through dataset {}", datasetKey);
        return new BatchConsumer<>(new Consumer<List<NameUsageWrapper>>() {
          final NameUsageWrapperConverter converter = new NameUsageWrapperConverter();

          @Override
          public void accept(List<NameUsageWrapper> nameUsageWrappers) {
            // convert nu wrappers to make sure
            try {
              for (NameUsageWrapper nuw : nameUsageWrappers) {
                converter.toDocument(nuw);
              }
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }, 100);
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
      public Stats indexAll(int ... excludedDatasetKeys) {
        LOG.info("No Elastic Search configured. Passing through");
        return new Stats();
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