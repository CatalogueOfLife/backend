package org.col.db.mapper;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.SimpleNameClassification;
import org.col.api.search.NameUsageWrapper;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface NameUsageWrapperMapper {

  /**
   * Iterates over all taxa with their vernaculars for a given dataset and processes them with the supplied handler. This allows a single
   * query to efficiently stream all its values without keeping them in memory. The classification attached includes the taxon or synonym
   * itself!
   */
  void processDatasetUsages(@Param("datasetKey") Integer datasetKey,
                            ResultHandler<NameUsageWrapper> handler);
  
  /**
   * Process all catalogue usages from a given sector
   * @param datasetKey the sectors dataset key. MUST match sector. In theory possible to get in SQL, but to reduce complexity we prefer to submit it explicitly
   * @param sectorKey the sectors key
   * @param usageId the sectors target usage id matching the sectorKey
   */
  void processSectorUsages(@Param("datasetKey") Integer datasetKey,
                           @Param("sectorKey") Integer sectorKey,
                           @Param("usageId") String usageId,
                           ResultHandler<NameUsageWrapper> handler);

  /**
   * Iterates over all bare names not linked to a synonym or taxon for a given dataset and processes them with the supplied handler. This
   * allows a single query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetBareNames(@Param("datasetKey") Integer datasetKey,
                               @Nullable @Param("sectorKey") Integer sectorKey,
                               ResultHandler<NameUsageWrapper> handler);
  
  /**
   * Traverses a subtree returning classifications as list of simple names objects only.
   * The first SimpleName in each classification list represents the usage being processed.
   */
  void processTree(@Param("datasetKey") Integer datasetKey,
                   @Param("usageId") String usageId,
                   ResultHandler<SimpleNameClassification> handler);

  NameUsageWrapper get(@Param("datasetKey") int datasetKey,
                       @Param("id") String taxonId);

}
