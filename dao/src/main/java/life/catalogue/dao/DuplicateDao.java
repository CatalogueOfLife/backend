package life.catalogue.dao;

import com.google.common.base.Preconditions;
import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.DuplicateMapper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DuplicateDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateDao.class);
  
  private final SqlSession session;
  private final DuplicateMapper mapper;
  
  public DuplicateDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  public List<Duplicate> findNames(MatchingMode mode, Integer minSize, int datasetKey, NameCategory category, Set<Rank> ranks,
                                   Boolean rankDifferent, Boolean codeDifferent, Boolean authorshipDifferent, Page page) {
    return find(true, mode, minSize, datasetKey, null, null, category, ranks, null, authorshipDifferent, null, rankDifferent, codeDifferent,null, null, page);
  }

  /**
   *
   * @param mode the matching mode to detect duplicates - strict or fuzzy
   * @param minSize minimum number of duplicate names to exist. Defaults to 2
   * @param datasetKey the dataset to be analyzed
   * @param projectKey the project key decisions and sectors are for. Required if withDecision is given
   * @param sourceDatasetKey the source dataset within a project to analyze. Requires datasetKey to be a project or release
   * @param sectorKey optional sector to restrict the duplicates to a single sector. Requires datasetKey to be a project or release
   * @param category optional restriction to uni/bi/trinomials only
   * @param ranks optional restriction on ranks
   * @param status optional restriction on usage status
   * @param authorshipDifferent
   * @param acceptedDifferent
   * @param rankDifferent
   * @param codeDifferent
   * @param withDecision optionally filter duplicates to have or do not already have a decision
   */
  public List<Duplicate> findUsages(MatchingMode mode, Integer minSize, int datasetKey, Integer sourceDatasetKey, Integer sectorKey, NameCategory category,
                                    Set<Rank> ranks, Set<TaxonomicStatus> status,
                                    Boolean authorshipDifferent, Boolean acceptedDifferent, Boolean rankDifferent,
                                    Boolean codeDifferent, Boolean withDecision, Integer projectKey, Page page) {
    return find(false, mode, minSize, datasetKey, sourceDatasetKey, sectorKey, category, ranks, status,
      authorshipDifferent, acceptedDifferent, rankDifferent, codeDifferent, withDecision, projectKey, page);
  }
  
  private List<Duplicate> find(boolean compareNames, MatchingMode mode, Integer minSize, int datasetKey, Integer sourceDatasetKey, Integer sectorKey, NameCategory category, Set<Rank> ranks,
                              Set<TaxonomicStatus> status, Boolean authorshipDifferent, Boolean acceptedDifferent,
                               Boolean rankDifferent, Boolean codeDifferent, Boolean withDecision, Integer projectKey, Page page) {
    mode = ObjectUtils.defaultIfNull(mode, MatchingMode.STRICT);
    minSize = ObjectUtils.defaultIfNull(minSize, 2);
    Preconditions.checkArgument(minSize > 1, "minimum group size must at least be 2");
    if (withDecision != null) {
      Preconditions.checkArgument(projectKey != null, "projectKey is required if parameter withDecision is used");
    }
    if (sourceDatasetKey != null){
      Preconditions.checkArgument(DatasetInfoCache.CACHE.origin(datasetKey).isManagedOrRelease(), "datasetKey must be a project or release if parameter sourceDatasetKey is used");
    }
    if (sectorKey != null){
      Preconditions.checkArgument(DatasetInfoCache.CACHE.origin(datasetKey).isManagedOrRelease(), "datasetKey must be a project or release if parameter sectorKey is used");
    }
    page = ObjectUtils.defaultIfNull(page, new Page());
    // load all duplicate usages or names
    List<Duplicate.Mybatis> dupsTmp;
    if (compareNames) {
      dupsTmp = mapper.duplicateNames(mode, minSize, datasetKey, category, ranks, authorshipDifferent, rankDifferent, codeDifferent, page);
    } else {
      dupsTmp = mapper.duplicates(mode, minSize, datasetKey, sourceDatasetKey, sectorKey, category, ranks, status,
        authorshipDifferent, acceptedDifferent, rankDifferent, codeDifferent, withDecision, projectKey, page);
    }
    if (dupsTmp.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    
    Set<String> ids = dupsTmp.stream()
        .map(Duplicate.Mybatis::getUsages)
        .flatMap(List::stream)
        .collect(Collectors.toSet());
    
    Map<String, Duplicate.UsageDecision> usages;
    if (compareNames) {
      usages = mapper.namesByIds(datasetKey, ids).stream()
          .collect(Collectors.toMap(d -> d.getUsage().getName().getId(), Function.identity()));
    } else {
      usages = mapper.usagesByIds(datasetKey, projectKey, ids).stream()
          .collect(Collectors.toMap(d -> d.getUsage().getId(), Function.identity()));
    }
    
    List<Duplicate> dups = new ArrayList<>(dupsTmp.size());
    for (Duplicate.Mybatis dm : dupsTmp) {
      Duplicate d = new Duplicate();
      d.setKey(dm.getKey());
      d.setUsages(dm.getUsages().stream()
          .map(usages::get)
          .collect(Collectors.toList())
      );
      dups.add(d);
    }
    return dups;
  }
}
