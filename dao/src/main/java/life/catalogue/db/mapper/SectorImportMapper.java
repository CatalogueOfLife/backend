package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.ImportMetrics;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.type2.StringCount;
import org.apache.ibatis.annotations.Param;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * The MyBatis mapper interface for SectorImport.
 */
public interface SectorImportMapper extends DatasetProcessable<SectorImport> {
  
  /**
   * Retrieves the full import with the entire potentially very large text tree and names id set.
   */
  SectorImport get(@Param("key") DSID<Integer> sectorKey, @Param("attempt") int attempt);

  /**
   * Get aggregated metrics for all sectors that share the same subjectDatasetKey within a given project
   * @param datasetKey the project key
   * @param subjectDatasetKey
   * @return aggregated sector metrics
   */
  ImportMetrics getBySourceDataset(@Param("datasetKey") int datasetKey, @Param("subjectDatasetKey") int subjectDatasetKey);

  /**
   * Count all imports by their state
   */
  int count(@Param("sectorKey") @Nullable Integer sectorKey,
            @Param("datasetKey") @Nullable Integer datasetKey,
            @Param("subjectDatasetKey") @Nullable Integer subjectDatasetKey,
            @Param("states") Collection<ImportState> states);
  
  /**
   * List all imports optionally filtered by their sectorKey and state(s).
   * Ordered by starting date from latest to historical.
   *
   * @param current if true only lists the attempt that was last successful and is currently referred to from the sector.
   */
  List<SectorImport> list(@Param("sectorKey") @Nullable Integer sectorKey,
                          @Param("datasetKey") @Nullable Integer datasetKey,
                          @Param("subjectDatasetKey") @Nullable Integer subjectDatasetKey,
                          @Param("states") @Nullable Collection<ImportState> states,
                          @Param("current") @Nullable Boolean current,
                          @Param("page") @Nullable Page page);
  
  void create(@Param("imp") SectorImport sectorImport);

  void update(@Param("imp") SectorImport sectorImport);

  /**
   * Deletes all imports for the given sector
   */
  int delete(@Param("key") DSID<Integer> sectorKey);


  Integer countBareName(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countDistribution(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countMedia(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countName(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countReference(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countSynonym(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countTaxon(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countTreatment(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countTypeMaterial(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  Integer countVernacular(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);

  List<StringCount> countDistributionsByGazetteer(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countIssues(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countMediaByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countNameRelationsByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countNamesByOrigin(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countNamesByRank(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countNamesByStatus(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countNamesByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countTaxaByRank(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countTaxonRelationsByType(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countTypeMaterialByStatus(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countUsagesByStatus(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  List<StringCount> countVernacularsByLanguage(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);
  
}
