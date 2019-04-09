package org.col.es;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Sector;
import org.col.api.search.NameUsageWrapper;
import org.col.db.mapper.BatchResultHandler;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.NameUsageMapper;
import org.col.db.mapper.SectorMapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.EsSearchRequest;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;
import static org.col.es.SynonymResultHandler.KeyType.SECTOR;

public class NameUsageIndexServiceEs implements NameUsageIndexService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexServiceEs.class);

  private final RestClient client;
  private final EsConfig esConfig;
  private final String index;
  private final SqlSessionFactory factory;

  public NameUsageIndexServiceEs(RestClient client, EsConfig esConfig, SqlSessionFactory factory) {
    this.client = client;
    this.index = esConfig.indexName(ES_INDEX_NAME_USAGE);
    this.esConfig = esConfig;
    this.factory = factory;
  }

  @Override
  public void indexDataset(Integer datasetKey) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, sCount, bCount;
    try (SqlSession session = factory.openSession()) {
      createOrEmptyIndex(datasetKey);
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing taxa for dataset {}", datasetKey);
        mapper.processDatasetTaxa(datasetKey, null, handler);
      }
      EsUtil.refreshIndex(client, index); // Necessary
      tCount = indexer.documentsIndexed();
      indexer.reset();
      try (SynonymResultHandler handler = new SynonymResultHandler(indexer, datasetKey)) {
        LOG.debug("Indexing synonyms for dataset {}", datasetKey);
        mapper.processDatasetSynonyms(datasetKey, null, handler);
      }
      EsUtil.refreshIndex(client, index); // Optional
      sCount = indexer.documentsIndexed();
      indexer.reset();
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing bare names for dataset {}", datasetKey);
        mapper.processDatasetBareNames(datasetKey, null, handler);
      }
      EsUtil.refreshIndex(client, index);
      bCount = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    logDatasetTotals(datasetKey, tCount, sCount, bCount);
  }

  @Override
  public void indexSector(Integer sectorKey) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount, sCount, bCount;
    try (SqlSession session = factory.openSession()) {
      Integer datasetKey = clearSector(session, sectorKey);
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing taxa for sector {}", sectorKey);
        mapper.processDatasetTaxa(datasetKey, sectorKey, handler);
      }
      EsUtil.refreshIndex(client, index); // Necessary
      tCount = indexer.documentsIndexed();
      indexer.reset();
      try (SynonymResultHandler handler = new SynonymResultHandler(indexer, sectorKey, SECTOR)) {
        LOG.debug("Indexing synonyms for sector {}", sectorKey);
        mapper.processDatasetSynonyms(datasetKey, sectorKey, handler);
      }
      EsUtil.refreshIndex(client, index); // Optional
      sCount = indexer.documentsIndexed();
      indexer.reset();
      try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
        LOG.debug("Indexing bare names for sector {}", sectorKey);
        mapper.processDatasetBareNames(datasetKey, sectorKey, handler);
      }
      EsUtil.refreshIndex(client, index);
      bCount = indexer.documentsIndexed();
    } catch (IOException e) {
      throw new EsException(e);
    }
    logSectorTotals(sectorKey, tCount, sCount, bCount);
  }
  
  @Override
  public void deleteSector(Integer sectorKey) {
    try (SqlSession session = factory.openSession()) {
      clearSector(session, sectorKey);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }
  
  @Override
  public void indexTaxa(Integer datasetKey, Collection<String> taxonIds) {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      List<NameUsageWrapper> taxa = taxonIds.stream()
          .map(id -> mapper.get(datasetKey, id))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      LOG.info("Indexing {} taxa from dataset {}", taxa.size(), datasetKey);
      indexer.accept(taxa);
      EsUtil.refreshIndex(client, index); // Necessary
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @Override
  public void indexAll() {
    NameUsageIndexer indexer = new NameUsageIndexer(client, index);
    int tCount = 0, sCount = 0, bCount = 0;
    try (SqlSession session = factory.openSession()) {
      EsUtil.deleteIndex(client, index);
      EsUtil.createIndex(client, index, esConfig.nameUsage);
      List<Integer> keys = session.getMapper(DatasetMapper.class).keys();
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      for (Integer datasetKey : keys) {
        int tc, sc, bc;
        try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
          LOG.debug("Indexing taxa for dataset {}", datasetKey);
          mapper.processDatasetTaxa(datasetKey, null, handler);
        }
        EsUtil.refreshIndex(client, index);
        tc = indexer.documentsIndexed();
        indexer.reset();
        try (SynonymResultHandler handler = new SynonymResultHandler(indexer, datasetKey)) {
          LOG.debug("Indexing synonyms for dataset {}", datasetKey);
          mapper.processDatasetSynonyms(datasetKey, null, handler);
        }
        EsUtil.refreshIndex(client, index);
        sc = indexer.documentsIndexed();
        indexer.reset();
        try (BatchResultHandler<NameUsageWrapper> handler = new BatchResultHandler<>(indexer, 4096)) {
          LOG.debug("Indexing bare names for dataset {}", datasetKey);
          mapper.processDatasetBareNames(datasetKey, null, handler);
        }
        EsUtil.refreshIndex(client, index);
        bc = indexer.documentsIndexed();
        indexer.reset();
        tCount += tc;
        sCount += sc;
        bCount += bc;
        logDatasetTotals(datasetKey, tc, sc, bc);
      }
    } catch (IOException e) {
      throw new EsException(e);
    }
    logTotals(tCount, sCount, bCount);
  }

  private void createOrEmptyIndex(int datasetKey) throws IOException {
    if (EsUtil.indexExists(client, index)) {
      EsUtil.deleteDataset(client, index, datasetKey);
      EsUtil.refreshIndex(client, index);
    } else {
      EsUtil.createIndex(client, index, esConfig.nameUsage);
    }
  }
  
  /**
   * @return datasetKey of the deleted sector
   */
  private Integer clearSector(SqlSession session, Integer sectorKey) throws IOException {
    EsSearchRequest query = EsSearchRequest.emptyRequest();
    query.select("datasetKey").whereEquals("sectorKey", sectorKey).size(1);
    NameUsageSearchService svc = new NameUsageSearchService(index, client);
    List<EsNameUsage> result = svc.getDocuments(query);
    if (result.size() != 0) {
      int cnt = EsUtil.deleteSector(client, index, sectorKey);
      LOG.debug("Deleted all {} documents from sector {} from index {}", cnt, sectorKey, index);
      return result.get(0).getDatasetKey();
    }
    SectorMapper mapper = session.getMapper(SectorMapper.class);
    Sector sector = mapper.get(sectorKey);
    if (sector == null) {
      throw new IllegalArgumentException("No such sector: " + sectorKey);
    }
    return sector.getDatasetKey();
  }

  private void logDatasetTotals(int datasetKey, int tCount, int sCount, int bCount) {
    String fmt = "Successfully indexed dataset {}. Index: {}. Taxa: {}. Synonyms: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, datasetKey, index, tCount, sCount, bCount, (tCount + sCount + bCount));
  }

  private void logSectorTotals(int sectorKey, int tCount, int sCount, int bCount) {
    String fmt = "Successfully indexed sector {}. Index: {}. Taxa: {}. Synonyms: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, sectorKey, index, tCount, sCount, bCount, (tCount + sCount + bCount));
  }

  private void logTotals(int tCount, int sCount, int bCount) {
    String fmt = "Finished indexing datasets. Index: {}. Taxa: {}. Synonyms: {}. Bare names: {}. Total: {}.";
    LOG.info(fmt, index, tCount, sCount, bCount, (tCount + sCount + bCount));
  }
}
