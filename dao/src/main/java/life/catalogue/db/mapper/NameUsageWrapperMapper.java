package life.catalogue.db.mapper;

import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.search.NameUsageWrapper;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface NameUsageWrapperMapper {

  /**
   * Issues and decisions are included, but publisherKey and sector information is not retrieved!
   *
   * @param datasetKey
   * @param taxonId
   * @return
   */
  NameUsageWrapper get(@Param("datasetKey") int datasetKey, @Param("id") String taxonId);

  /**
   * Get bare name by its name id. If a usage with the name exists no bare name can be retrieved!
   * @return a wrapped bare name selected by its name id or null
   */
  NameUsageWrapper getBareName(@Param("datasetKey") int datasetKey, @Param("id") String nameId);

  /**
   * Iterates over all bare names not linked to a synonym or taxon for a given dataset. This
   * allows a single query to efficiently stream all its values without keeping them in memory.
   */
  Cursor<NameUsageWrapper> processDatasetBareNames(@Param("datasetKey") Integer datasetKey,
                               @Nullable @Param("sectorKey") Integer sectorKey);


  /**
   * Processes usages without classification and accepted Taxon for synonyms from a dataset, optional restricted to a single sector.
   * Results are ordered by status (accepted first) and rank (highest rank first).
   * This gets close to a tree traversal, but is more performant. Badly arranged trees and unranked or uncomparable taxa can cause unexpected problems
   * that should be catered for by the consumer.
   *
   * WARNING!
   * This method requires 2 temporary session bound tables "tmp_usage_issues" and "tmp_usage_sources" to exist.
   * Create it before using the VerbatimRecordMapper.createTmpIssuesTable method.
   *
   * @param datasetKey the dataset, e.g. catalogue, to process
   * @param sectorKey the optional sector to restrict the processed usages to
   */
  Cursor<NameUsageWrapper> processWithoutClassification(@Param("datasetKey") Integer datasetKey,
                                          @Nullable @Param("sectorKey") Integer sectorKey);

  /**
   * Traverses a subtree returning classifications as list of simple names objects only.
   * The first SimpleName in each classification list represents the usage being processed.
   *
   * @param datasetKey the dataset, e.g. catalogue, to process
   * @param sectorKey the optional sector to restrict the processed usages to
   * @param usageId the root usage to start processing the tree. Will be included in the processed result
   */
  Cursor<SimpleNameClassification> processTree(@Param("datasetKey") Integer datasetKey,
                      @Nullable @Param("sectorKey") Integer sectorKey,
                      @Param("usageId") String usageId);

}
