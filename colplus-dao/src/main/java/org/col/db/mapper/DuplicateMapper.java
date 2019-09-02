package org.col.db.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.MatchingMode;
import org.col.api.vocab.NameCategory;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

public interface DuplicateMapper {
  
  List<Duplicate.Mybatis> duplicateNames(@Param("mode") MatchingMode mode,
                                 @Param("minSize") Integer minSize,
                                 @Param("datasetKey") int datasetKey,
                                 @Param("category") NameCategory category,
                                 @Param("ranks") Set<Rank> ranks,
                                 @Param("authorshipDifferent") Boolean authorshipDifferent,
                                 @Param("rankDifferent") Boolean rankDifferent,
                                 @Param("codeDifferent") Boolean codeDifferent,
                                 @Param("page") Page page);
  
  List<Duplicate.UsageDecision> namesByIds(@Param("datasetKey") int datasetKey, @Param("ids") List<String> ids);
  
  
  List<Duplicate.Mybatis> duplicates(@Param("mode") MatchingMode mode,
                             @Param("minSize") Integer minSize,
                             @Param("datasetKey") int datasetKey,
                             @Param("sectorKey") Integer sectorKey,
                             @Param("category") NameCategory category,
                             @Param("ranks") Set<Rank> ranks,
                             @Param("status") Set<TaxonomicStatus> status,
                             @Param("authorshipDifferent") Boolean authorshipDifferent,
                             @Param("acceptedDifferent") Boolean acceptedDifferent,
                             @Param("rankDifferent") Boolean rankDifferent,
                             @Param("codeDifferent") Boolean codeDifferent,
                             @Param("withDecision") Boolean withDecision,
                             @Param("page") Page page);
  
  /**
   * @param ids usage ids to return usage decisions for
   */
  List<Duplicate.UsageDecision> usagesByIds(@Param("datasetKey") int datasetKey, @Param("ids") List<String> ids);
  
}
