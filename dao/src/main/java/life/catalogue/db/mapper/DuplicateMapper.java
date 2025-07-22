package life.catalogue.db.mapper;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DatasetInfoCache;

import org.gbif.nameparser.api.Rank;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

public interface DuplicateMapper {
  Logger LOG = LoggerFactory.getLogger(DuplicateMapper.class);

  /**
   * See DuplicateDao for parameter descriptions...
   */
  List<Duplicate.Mybatis> duplicateNames(@Param("mode") MatchingMode mode,
                                         @Param("query") String query,
                                         @Param("minSize") Integer minSize,
                                         @Param("datasetKey") int datasetKey,
                                         @Param("category") NameCategory category,
                                         @Param("ranks") Set<Rank> ranks,
                                         @Param("authorshipDifferent") Boolean authorshipDifferent,
                                         @Param("rankDifferent") Boolean rankDifferent,
                                         @Param("codeDifferent") Boolean codeDifferent,
                                         @Param("page") Page page);

  /**
   * @param ids usage ids to return usage decisions for
   */
  default List<Duplicate.UsageDecision> namesByIds(@Param("datasetKey") int datasetKey, Collection<String> ids) {
    LOG.info("Query {} duplicate names by id in dataset {}", ids.size(), datasetKey);
    createIdTable();
    Iterables.partition(ids, 10000).forEach(this::insertTableIdBatch);
    var res = namesByTableIds(datasetKey);
    dropIdTable();
    return res;
  }

  /**
   * See DuplicateDao for parameter descriptions...
   */
  List<Duplicate.Mybatis> duplicates(@Param("mode") MatchingMode mode,
                                     @Param("query") String query,
                                     @Param("minSize") Integer minSize,
                                     @Param("datasetKey") int datasetKey,
                                     @Param("sourceDatasetKey") Integer sourceDatasetKey,
                                     @Param("sectorKey") Integer sectorKey,
                                     @Param("sourceOnly") Boolean sourceOnly,
                                     @Param("category") NameCategory category,
                                     @Param("ranks") Set<Rank> ranks,
                                     @Param("status") Set<TaxonomicStatus> status,
                                     @Param("authorshipDifferent") Boolean authorshipDifferent,
                                     @Param("acceptedDifferent") Boolean acceptedDifferent,
                                     @Param("rankDifferent") Boolean rankDifferent,
                                     @Param("codeDifferent") Boolean codeDifferent,
                                     @Param("withDecision") Boolean withDecision,
                                     @Param("projectKey") Integer projectKey,
                                     @Param("page") Page page);

  /**
   * See DuplicateDao for parameter descriptions...
   */
  Integer count(@Param("mode") MatchingMode mode,
                @Param("query") String query,
                @Param("minSize") Integer minSize,
                @Param("datasetKey") int datasetKey,
                @Param("sourceDatasetKey") Integer sourceDatasetKey,
                @Param("sectorKey") Integer sectorKey,
                @Param("sourceOnly") Boolean sourceOnly,
                @Param("category") NameCategory category,
                @Param("ranks") Set<Rank> ranks,
                @Param("status") Set<TaxonomicStatus> status,
                @Param("authorshipDifferent") Boolean authorshipDifferent,
                @Param("acceptedDifferent") Boolean acceptedDifferent,
                @Param("rankDifferent") Boolean rankDifferent,
                @Param("codeDifferent") Boolean codeDifferent,
                @Param("withDecision") Boolean withDecision,
                @Param("projectKey") Integer projectKey);

  Integer countNames(@Param("mode") MatchingMode mode,
                     @Param("query") String query,
                     @Param("minSize") Integer minSize,
                     @Param("datasetKey") int datasetKey,
                     @Param("category") NameCategory category,
                     @Param("ranks") Set<Rank> ranks,
                     @Param("authorshipDifferent") Boolean authorshipDifferent,
                     @Param("rankDifferent") Boolean rankDifferent,
                     @Param("codeDifferent") Boolean codeDifferent);

  /**
   * @param ids usage ids to return usage decisions for
   */
  default List<Duplicate.UsageDecision> usagesByIds(@Param("datasetKey") int datasetKey, @Param("projectKey") Integer projectKey, Collection<String> ids) {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    LOG.info("Query {} duplicate usages by id in {} dataset {}", ids.size(), info.origin, datasetKey);
    createIdTable();
    Iterables.partition(ids, 10000).forEach(this::insertTableIdBatch);
    var res = usagesByTableIds(datasetKey, projectKey, info.origin.isProjectOrRelease());
    dropIdTable();
    return res;
  }

  //
  // INTERNAL METHODS _ DONT USE
  //

  @Deprecated
  List<Duplicate.UsageDecision> namesByTableIds(@Param("datasetKey") int datasetKey);

  @Deprecated
  List<Duplicate.UsageDecision> usagesByTableIds(@Param("datasetKey") int datasetKey,
                                                 @Param("projectKey") Integer projectKey,
                                                 @Param("addSrc") boolean addSrc
  );

  @Deprecated
  void createIdTable();

  @Deprecated
  void dropIdTable();

  @Deprecated
  void insertTableIdBatch(@Param("ids") Collection<String> ids);


  /**
   * Lists all homonyms, i.e. same accepted names with the same rank & code, but potentially different authorships.
   */
  List<Duplicate.Homonyms> homonyms(@Param("datasetKey") int datasetKey, @Param("status") Set<TaxonomicStatus> status);

}
