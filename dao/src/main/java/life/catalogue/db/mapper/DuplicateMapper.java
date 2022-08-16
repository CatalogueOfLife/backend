package life.catalogue.db.mapper;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

import com.google.common.collect.Iterables;

public interface DuplicateMapper {

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
    createIdTable();
    Iterables.partition(ids, 10000).forEach(this::insertTableIdBatch);
    var res = usagesByTableIds(datasetKey, projectKey);
    dropIdTable();
    return res;
  }

  //
  // INTERNAL METHODS _ DONT USE
  //

  @Deprecated
  List<Duplicate.UsageDecision> namesByTableIds(@Param("datasetKey") int datasetKey);
  @Deprecated
  List<Duplicate.UsageDecision> usagesByTableIds(@Param("datasetKey") int datasetKey, @Param("projectKey") Integer projectKey);
  @Deprecated
  void createIdTable();
  @Deprecated
  void dropIdTable();
  @Deprecated
  void insertTableIdBatch(@Param("ids") Collection<String> ids);
}
