package life.catalogue.dao;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.vocab.*;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.type2.StringCount;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class DatasetImportDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetImportDao.class);
  
  private final SqlSessionFactory factory;
  private final FileMetricsDatasetDao fileMetricsDao;
  
  
  public DatasetImportDao(SqlSessionFactory factory, File repo) {
    this.factory = factory;
    this.fileMetricsDao = new FileMetricsDatasetDao(factory, repo);
  }
  
  public FileMetricsDatasetDao getFileMetricsDao() {
    return fileMetricsDao;
  }
  
  /**
   * Pages through all imports ordered by starting date from most recent ones to historical.
   */
  public ResultPage<DatasetImport> list(Page page) {
    return list(null, null, page);
  }
  
  /**
   * List all imports optionally filtered by their datasetKey and state(s).
   * Ordered by starting date from latest to historical.
   */
  public ResultPage<DatasetImport> list(Integer datasetKey, Collection<ImportState> states, Page page) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      List<DatasetImport> result = mapper.list(datasetKey, states, page);
      return new ResultPage<>(page, result, () -> mapper.count(datasetKey, states));
    }
  }

  /**
   * Create a new waiting dataset import with the next attempt
   */
  public DatasetImport createWaiting(int datasetKey, Runnable job, int user) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(datasetKey);
    di.setCreatedBy(user);
    di.setStarted(LocalDateTime.now());
    di.setDownloadUri(null);
    di.setOrigin(DatasetInfoCache.CACHE.info(datasetKey).origin);
    di.setJob(job.getClass().getSimpleName());
    di.setState(ImportState.WAITING);
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetImportMapper.class).create(di);
    }
    return di;
  }
  
  public void updateDatasetLastAttempt(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).updateLastImport(di.getDatasetKey(), di.getAttempt());
    }
  }
  
  public DatasetImport getLast(int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(DatasetImportMapper.class).last(datasetKey);
    }
  }
  
  public DatasetImport getAttempt(int datasetKey, int attempt) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(DatasetImportMapper.class).get(datasetKey, attempt);
    }
  }
  
  /**
   * Generates new metrics, but does not persist them as an import record.
   */
  public DatasetImport generateMetrics(int datasetKey, int user) {
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(datasetKey);
    di.setCreatedBy(user);
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      updateMetrics(mapper, di, datasetKey);
    }
    return di;
  }

  /**
   * Update all metrics for the given dataset import and dataset key.
   * The key is given explicitly because it might deviate from the datasetKey of the DatasetImport
   * in case of releases, where we store the release metrics in the mother project (which is the DatasetImport.datasetKey).
   *
   * @param di import to update
   * @param key the dataset key to analyze the data from. Should be the release datasetKey for releases
   */
  public void updateMetrics(DatasetImport di, int key) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      updateMetrics(mapper, di, key);
  
      fileMetricsDao.updateTree(key, di.getDatasetKey(), di.getAttempt());
      fileMetricsDao.updateNames(key, di.getDatasetKey(), di.getAttempt());
      
    } catch (IOException e) {
      LOG.error("Failed to update metrics for dataset {} from dataset {}", di.getDatasetKey(), key, e);
    }
  }

  /**
   * @param di import to update
   * @param key the dataset key to analyze the data from
   */
  private void updateMetrics(DatasetImportMapper mapper, DatasetImport di, int key) {
    di.setBareNameCount(mapper.countBareName(key));
    di.setDistributionCount(mapper.countDistribution(key));
    di.setEstimateCount(mapper.countEstimate(key));
    di.setMediaCount(mapper.countMedia(key));
    di.setNameCount(mapper.countName(key));
    di.setReferenceCount(mapper.countReference(key));
    di.setSynonymCount(mapper.countSynonym(key));
    di.setTaxonCount(mapper.countTaxon(key));
    di.setTreatmentCount(mapper.countTreatment(key));
    di.setTypeMaterialCount(mapper.countTypeMaterial(key));
    di.setVerbatimCount(mapper.countVerbatim(key));
    di.setVernacularCount(mapper.countVernacular(key));

    di.setDistributionsByGazetteerCount(countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(key)));
    di.setExtinctTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countExtinctTaxaByRank(key)));
    di.setIssuesCount(countMap(Issue.class, mapper.countIssues(key)));
    di.setMediaByTypeCount(countMap(MediaType.class, mapper.countMediaByType(key)));
    di.setNameRelationsByTypeCount(countMap(NomRelType.class, mapper.countNameRelationsByType(key)));
    di.setNamesByCodeCount(countMap(NomCode.class, mapper.countNamesByCode(key)));
    di.setNamesByRankCount(countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(key)));
    di.setNamesByStatusCount(countMap(NomStatus.class, mapper.countNamesByStatus(key)));
    di.setNamesByTypeCount(countMap(NameType.class, mapper.countNamesByType(key)));
    di.setSpeciesInteractionsByTypeCount(countMap(SpeciesInteractionType.class, mapper.countSpeciesInteractionsByType(key)));
    di.setSynonymsByRankCount(countMap(DatasetImportDao::parseRank, mapper.countSynonymsByRank(key)));
    di.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(key)));
    di.setTaxonConceptRelationsByTypeCount(countMap(TaxonConceptRelType.class, mapper.countTaxonConceptRelationsByType(key)));
    di.setTypeMaterialByStatusCount(countMap(TypeStatus.class, mapper.countTypeMaterialByStatus(key)));
    di.setUsagesByOriginCount(countMap(Origin.class, mapper.countUsagesByOrigin(key)));
    di.setUsagesByStatusCount(countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(key)));
    di.setVerbatimByTermCount(countMap(DatasetImportDao::parseRowType, mapper.countVerbatimByType(key)));
    di.setVernacularsByLanguageCount(countMap(mapper.countVernacularsByLanguage(key)));

    // verbatim term metrics for each row type
    for (Term rowType : di.getVerbatimByTermCount().keySet()) {
      Map<Term, Integer> terms = countMap(DatasetImportDao::parseTerm, mapper.countVerbatimTerms(key, rowType));
      if (!terms.isEmpty()) {
        di.getVerbatimByRowTypeCount().put(rowType, terms);
      }
    }
  }
  
  public static Map<String, Integer> countMap(List<StringCount> counts) {
    Map<String, Integer> map = new HashMap<>(counts.size());
    for (StringCount cnt : counts) {
      if (cnt.getKey() != null) {
        map.put(cnt.getKey(), cnt.getCount());
      }
    }
    return map;
  }
  
  public static <K extends Enum<K>> Map<K, Integer> countMap(Class<K> clazz, List<StringCount> counts) {
    Map<K, Integer> map = new HashMap<>(counts.size());
    for (StringCount cnt : counts) {
      if (cnt.getKey() != null) {
        map.put(Enum.valueOf(clazz, cnt.getKey()), cnt.getCount());
      }
    }
    return map;
  }
  
  public static <K> Map<K, Integer> countMap(Function<String, Optional<K>> converter, List<StringCount> counts) {
    Map<K, Integer> map = new HashMap<>(counts.size());
    for (StringCount cnt : counts) {
      if (!Strings.isNullOrEmpty(cnt.getKey())) {
        Optional<K> opt = converter.apply(cnt.getKey());
        opt.ifPresent(k -> map.put(k, cnt.getCount()));
      }
    }
    return map;
  }
  
  public static Optional<Rank> parseRank(String rank) {
    return Optional.of(Rank.valueOf(rank.toUpperCase()));
  }
  
  private static Optional<Term> parseRowType(String term) {
    return Optional.of(TermFactory.instance().findClassTerm(term));
  }
  
  private static Optional<Term> parseTerm(String term) {
    return Optional.of(TermFactory.instance().findPropertyTerm(term));
  }

  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportCancelled(DatasetImport di) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.CANCELED);
    di.setError(null);
    update(di);
  }
  
  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportFailure(DatasetImport di, Throwable e) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.FAILED);
    // System.out.println(ExceptionUtils.getMessage(e));
    di.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
    update(di);
  }
  
  public void update(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)) {
      update(di, session.getMapper(DatasetImportMapper.class));
    }
  }
  
  private void update(DatasetImport di, DatasetImportMapper mapper) {
    Preconditions.checkNotNull(di.getDatasetKey(), "datasetKey required for update");
    Preconditions.checkNotNull(di.getAttempt(), "attempt required for update");
    mapper.update(di);
  }
  
  public void removeMetrics(int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      mapper.deleteByDataset(datasetKey);
      fileMetricsDao.deleteAll(datasetKey);
      
    } catch (IOException e) {
      LOG.error("Failed to remove all metrics for dataset {}", datasetKey, e);
    }
  }
}
