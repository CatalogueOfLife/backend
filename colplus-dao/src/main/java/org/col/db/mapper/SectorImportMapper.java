package org.col.db.mapper;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.SectorImport;
import org.col.db.type2.IntCount;
import org.col.db.type2.StringCount;

/**
 * The MyBatis mapper interface for SectorImport.
 */
public interface SectorImportMapper {
  
  /**
   * Retrieves the full import with the entire potentially very large text tree and names id set.
   */
  SectorImport get(@Param("key") int sectorKey, @Param("attempt") int attempt);
  
  /**
   * Count all imports by their state
   */
  int count(@Param("key") @Nullable Integer sectorKey, @Param("states") Collection<SectorImport.State> states);
  
  /**
   * List all imports optionally filtered by their sectorKey and state(s).
   * Ordered by starting date from latest to historical.
   */
  List<SectorImport> list(@Param("key") @Nullable Integer sectorKey,
                           @Param("states") @Nullable Collection<SectorImport.State> states,
                           @Param("page") Page page);
  
  void create(@Param("imp") SectorImport sectorImport);
  
  /**
   * Deletes all imports for the given sector
   */
  int delete(@Param("key") int sectorKey);
  
  
  Integer countDescription(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countDistribution(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countMedia(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countName(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  Integer countReference(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countTaxon(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countSynonym(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countVernacular(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countIssues(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
  List<IntCount> countDistributionsByGazetteer(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countMediaByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countNameRelationsByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countNamesByOrigin(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countNamesByStatus(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countNamesByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<IntCount> countUsagesByStatus(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countNamesByRank(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countTaxaByRank(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countVernacularsByLanguage(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
}
