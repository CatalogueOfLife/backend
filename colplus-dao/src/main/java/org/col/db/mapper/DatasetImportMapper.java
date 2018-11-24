package org.col.db.mapper;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.vocab.ImportState;
import org.col.db.type2.IntCount;
import org.col.db.type2.StringCount;

/**
 * The MyBatis mapper interface for DatasetImport.
 */
public interface DatasetImportMapper {
  
  DatasetImport get(@Param("key") int datasetKey, @Param("attempt") int attempt);
  
  /**
   * Count all imports by their state
   */
  int count(@Param("key") @Nullable Integer datasetKey, @Param("states") Collection<ImportState> states);
  
  /**
   * List all imports optionally filtered by their datasetKey and state(s).
   * Ordered by starting date from latest to historical.
   */
  List<DatasetImport> list(@Param("key") @Nullable Integer datasetKey,
                           @Param("states") @Nullable Collection<ImportState> states,
                           @Param("page") Page page);
  
  void create(@Param("di") DatasetImport datasetImport);
  
  void update(@Param("di") DatasetImport datasetImport);
  
  Integer countDescription(@Param("key") int datasetKey);
  Integer countDistribution(@Param("key") int datasetKey);
  Integer countMedia(@Param("key") int datasetKey);
  Integer countName(@Param("key") int datasetKey);
  
  Integer countReference(@Param("key") int datasetKey);
  
  Integer countTaxon(@Param("key") int datasetKey);
  
  Integer countVerbatim(@Param("key") int datasetKey);
  
  Integer countVernacular(@Param("key") int datasetKey);
  
  List<IntCount> countDistributionsByGazetteer(@Param("key") int datasetKey);
  
  List<IntCount> countIssues(@Param("key") int datasetKey);
  List<IntCount> countMediaByType(@Param("key") int datasetKey);
  List<IntCount> countNameRelationsByType(@Param("key") int datasetKey);
  
  List<IntCount> countNamesByOrigin(@Param("key") int datasetKey);
  List<IntCount> countNamesByStatus(@Param("key") int datasetKey);
  
  List<IntCount> countNamesByType(@Param("key") int datasetKey);
  
  List<IntCount> countUsagesByStatus(@Param("key") int datasetKey);
  List<StringCount> countNamesByRank(@Param("key") int datasetKey);
  List<StringCount> countTaxaByRank(@Param("key") int datasetKey);
  List<StringCount> countVerbatimByType(@Param("key") int datasetKey);
  
  List<StringCount> countVernacularsByLanguage(@Param("key") int datasetKey);
  
  
}
