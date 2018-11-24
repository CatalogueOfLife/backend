package org.col.db.dao;

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
  
  
  public DatasetImportDao(SqlSessionFactory factory) {
    this.factory = factory;
  }
  
  public ResultPage<DatasetImport> list(Integer datasetKey, Collection<ImportState> states, Page page) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      return new ResultPage<>(page, mapper.count(datasetKey, states), mapper.list(datasetKey, states, page));
    }
  }
  
  /**
   * Create a new downloading dataset import with the next attempt
   */
  public DatasetImport createDownloading(Dataset d) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(d.getKey());
    di.setState(ImportState.DOWNLOADING);
    di.setDownloadUri(d.getDataAccess());
    di.setStarted(LocalDateTime.now());
    
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
    return di;
  }
  
  public DatasetImport getLast(Dataset d) {
    try (SqlSession session = factory.openSession(true)) {
      Page p = new Page(0, 1);
      List<DatasetImport> imports = session.getMapper(DatasetImportMapper.class).list(d.getKey(), null, p);
      return imports == null || imports.isEmpty() ? null : imports.get(0);
    }
  }
  
  /**
   * Updates a running dataset import instance with metrics and success state.
   * Updates the dataset to point to the imports attempt.
   */
  public void updateImportSuccess(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      // update count metrics
      updateMetrics(mapper, di);
      di.setFinished(LocalDateTime.now());
      di.setState(ImportState.FINISHED);
      di.setError(null);
      update(di, mapper);
      
      session.getMapper(DatasetMapper.class).updateLastImport(di.getDatasetKey(), di.getAttempt());
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
  
  private void updateMetrics(DatasetImportMapper mapper, DatasetImport di) {
    final int key = di.getDatasetKey();
  
    di.setDescriptionCount(mapper.countDescription(key));
    di.setDistributionCount(mapper.countDistribution(key));
    di.setMediaCount(mapper.countMedia(key));
    di.setNameCount(mapper.countName(key));
    di.setReferenceCount(mapper.countReference(key));
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
    di.setVernacularsByLanguageCount(countMap(Language::fromIsoCode, mapper.countVernacularsByLanguage(key)));
  }
  
  private static <K extends Enum> Map<K, Integer> countMap(Class<K> clazz, List<IntCount> counts) {
    K[] values = clazz.getEnumConstants();
    Map<K, Integer> map = new HashMap<>(counts.size());
    for (IntCount cnt : counts) {
      if (cnt.getKey() != null) {
        map.put(values[cnt.getKey()], cnt.getCount());
      }
    }
    return map;
  }
  
  private static <K> Map<K, Integer> countMap(Function<String, Optional<K>> converter, List<StringCount> counts) {
    Map<K, Integer> map = new HashMap<>(counts.size());
    for (StringCount cnt : counts) {
      if (!Strings.isNullOrEmpty(cnt.getKey())) {
        Optional<K> opt = converter.apply(cnt.getKey());
        opt.ifPresent(k -> map.put(k, cnt.getCount()));
      }
    }
    return map;
  }
  
  private static Optional<Rank> parseRank(String rank) {
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
  
  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportUnchanged(DatasetImport di) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.UNCHANGED);
    di.setError(null);
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
