package life.catalogue.db.mapper;

import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.apache.ibatis.annotations.Param;
import org.gbif.nameparser.api.Rank;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
  
  List<Duplicate.UsageDecision> namesByIds(@Param("datasetKey") int datasetKey, @Param("ids") Collection<String> ids);
  
  
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
                             @Param("catalogueKey") Integer catalogueKey,
                             @Param("page") Page page);
  
  /**
   * @param ids usage ids to return usage decisions for
   */
  List<Duplicate.UsageDecision> usagesByIds(@Param("datasetKey") int datasetKey, @Param("ids") Collection<String> ids);
  
}
