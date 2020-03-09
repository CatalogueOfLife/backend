package life.catalogue.dao;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.vocab.*;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.type2.StringCount;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

public class DatasetImportDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetImportDao.class);
  
  private final SqlSessionFactory factory;
  private final NamesTreeDao treeDao;
  
  
  public DatasetImportDao(SqlSessionFactory factory, File repo) {
    this.factory = factory;
    this.treeDao = new NamesTreeDao(factory, repo);
  }
  
  public NamesTreeDao getTreeDao() {
    return treeDao;
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
   * Create a new downloading dataset import with the next attempt
   */
  public DatasetImport create(Dataset d, int user) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(d.getKey());
    di.setCreatedBy(user);
    di.setStarted(LocalDateTime.now());
    di.setDownloadUri(null);
    di.setOrigin(d.getOrigin());
    di.setFormat(d.getDataFormat());
    if (d.getOrigin() == DatasetOrigin.EXTERNAL) {
      di.setState(ImportState.DOWNLOADING);
      di.setDownloadUri(d.getDataAccess());
    } else if (d.getOrigin() == DatasetOrigin.MANAGED) {
      di.setState(ImportState.WAITING);
    } else {
      di.setState(ImportState.PROCESSING);
    }
    
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetImportMapper.class).create(di);
    }
    
    return di;
  }

  /**
   * Generates new metrics and persists them as a new successful import record.
   * Use this only for tests - should be moved to test code !!!
   */
  @Deprecated
  public DatasetImport createSuccess(int datasetKey, int user) {
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(datasetKey);
    di.setCreatedBy(user);
    di.setState(ImportState.FINISHED);
    di.setDownloadUri(null);
    di.setOrigin(DatasetOrigin.UPLOADED);
    di.setFormat(DataFormat.COLDP);
    di.setStarted(LocalDateTime.now());
    di.setDownload(LocalDateTime.now());
    di.setFinished(LocalDateTime.now());
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      updateMetrics(mapper, di);
      mapper.create(di);
    }
    // also update dataset with attempt
    updateDatasetLastAttempt(di);
    return di;
  }
  
  public void updateDatasetLastAttempt(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).updateLastImport(di.getDatasetKey(), di.getAttempt());
    }
  }
  
  public DatasetImport getLast(int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      Page p = new Page(0, 1);
      List<DatasetImport> imports = session.getMapper(DatasetImportMapper.class).list(datasetKey, null, p);
      return imports == null || imports.isEmpty() ? null : imports.get(0);
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
      updateMetrics(mapper, di);
    }
    return di;
  }
  
  public void updateMetrics(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      updateMetrics(mapper, di);
  
      treeDao.updateDatasetTree(di.getDatasetKey(), di.getAttempt());
      treeDao.updateDatasetNames(di.getDatasetKey(), di.getAttempt());
      
    } catch (IOException e) {
      LOG.error("Failed to print text tree for dataset {}", di.getDatasetKey(), e);
    }
  }
  
  private void updateMetrics(DatasetImportMapper mapper, DatasetImport di) {
    final int key = di.getDatasetKey();
  
    di.setDescriptionCount(mapper.countDescription(key));
    di.setDistributionCount(mapper.countDistribution(key));
    di.setMediaCount(mapper.countMedia(key));
    di.setNameCount(mapper.countName(key));
    di.setTypeMaterialCount(mapper.countTypeMaterial(key));
    di.setReferenceCount(mapper.countReference(key));
    di.setSynonymCount(mapper.countSynonym(key));
    di.setTaxonCount(mapper.countTaxon(key));
    di.setVerbatimCount(mapper.countVerbatim(key));
    di.setVernacularCount(mapper.countVernacular(key));
  
    di.setDistributionsByGazetteerCount(DatasetImportDao.countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(key)));
    di.setIssuesCount(DatasetImportDao.countMap(Issue.class, mapper.countIssues(key)));
    di.setMediaByTypeCount(DatasetImportDao.countMap(MediaType.class, mapper.countMediaByType(key)));
    di.setNameRelationsByTypeCount(DatasetImportDao.countMap(NomRelType.class, mapper.countNameRelationsByType(key)));
    di.setNamesByOriginCount(DatasetImportDao.countMap(Origin.class, mapper.countNamesByOrigin(key)));
    di.setNamesByRankCount(countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(key)));
    di.setNamesByStatusCount(DatasetImportDao.countMap(NomStatus.class, mapper.countNamesByStatus(key)));
    di.setNamesByTypeCount(DatasetImportDao.countMap(NameType.class, mapper.countNamesByType(key)));
    di.setTypeMaterialByStatusCount(DatasetImportDao.countMap(TypeStatus.class, mapper.countTypeMaterialByStatus(key)));
    di.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(key)));
    di.setUsagesByStatusCount(DatasetImportDao.countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(key)));
    di.setVerbatimByTypeCount(countMap(DatasetImportDao::parseRowType, mapper.countVerbatimByType(key)));
    di.setVernacularsByLanguageCount(countMap(mapper.countVernacularsByLanguage(key)));
    
    // verbatim term metrics for each row type
    for (Term rowType : di.getVerbatimByTypeCount().keySet()) {
      Map<Term, Integer> terms = countMap(DatasetImportDao::parseTerm, mapper.countVerbatimTerms(key, rowType));
      if (!terms.isEmpty()) {
        di.getVerbatimByTermCount().put(rowType, terms);
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
      treeDao.deleteByDataset(datasetKey);
      
    } catch (IOException e) {
      LOG.error("Failed to remove all metrics for dataset {}", datasetKey, e);
    }
  }
}
