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

public interface DuplicateMapper {

  /**
   * See DuplicateDao for parameter descriptions...
   */
  List<Duplicate.Mybatis> duplicateNames(@Param("mode") MatchingMode mode,
                                 @Param("minSize") Integer minSize,
                                 @Param("datasetKey") int datasetKey,
                                 @Param("category") NameCategory category,
                                 @Param("ranks") Set<Rank> ranks,
                                 @Param("authorshipDifferent") Boolean authorshipDifferent,
                                 @Param("rankDifferent") Boolean rankDifferent,
                                 @Param("codeDifferent") Boolean codeDifferent,
                                 @Param("page") Page page);
  
  List<Duplicate.UsageDecision> namesByIds(@Param("datasetKey") int datasetKey, @Param("ids") Collection<String> ids);


  /**
   * See DuplicateDao for parameter descriptions...
   */
  List<Duplicate.Mybatis> duplicates(@Param("mode") MatchingMode mode,
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
   * @param ids usage ids to return usage decisions for
   */
  List<Duplicate.UsageDecision> usagesByIds(@Param("datasetKey") int datasetKey, @Param("projectKey") Integer projectKey, @Param("ids") Collection<String> ids);

}
