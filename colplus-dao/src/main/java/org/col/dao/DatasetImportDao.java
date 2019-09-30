package org.col.dao;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.*;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.col.db.type2.IntCount;
import org.col.db.type2.StringCount;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public DatasetImport create(Dataset d) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(d.getKey());
    di.setStarted(LocalDateTime.now());
    if (d.getOrigin() == DatasetOrigin.EXTERNAL) {
      di.setState(ImportState.DOWNLOADING);
      di.setDownloadUri(d.getDataAccess());
    } else {
      di.setState(ImportState.PROCESSING);
      di.setDownloadUri(null);
    }
    
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetImportMapper.class).create(di);
    }
    
    return di;
  }
  
  /**
   * Generates new metrics and persists them as a new successful import record.
   */
  public DatasetImport createSuccess(int datasetKey) {
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(datasetKey);
    di.setState(ImportState.FINISHED);
    di.setDownloadUri(null);
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
  public DatasetImport generateMetrics(int datasetKey) {
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(datasetKey);
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
    di.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(key)));
    di.setUsagesByStatusCount(DatasetImportDao.countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(key)));
    di.setVerbatimByTypeCount(countMap(DatasetImportDao::parseTerm, mapper.countVerbatimByType(key)));
    di.setVernacularsByLanguageCount(countMap(mapper.countVernacularsByLanguage(key)));
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
  
  public static <K extends Enum> Map<K, Integer> countMap(Class<K> clazz, List<IntCount> counts) {
    K[] values = clazz.getEnumConstants();
    Map<K, Integer> map = new HashMap<>(counts.size());
    for (IntCount cnt : counts) {
      if (cnt.getKey() != null) {
        map.put(values[cnt.getKey()], cnt.getCount());
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
  
  private static Optional<Term> parseTerm(String term) {
    return Optional.of(TermFactory.instance().findClassTerm(term));
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
  public void updateImportFailure(DatasetImport di, Exception e) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.FAILED);
    // System.out.println(ExceptionUtils.getMessage(e));
    di.setError(e.getMessage());
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
  
}
