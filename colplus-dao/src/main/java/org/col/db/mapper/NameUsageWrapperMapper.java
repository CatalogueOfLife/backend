package org.col.db.mapper;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
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
                            @Nullable @Param("sectorKey") Integer sectorKey,
                            ResultHandler<NameUsageWrapper> handler);

  /**
   * Iterates over all bare names not linked to a synonym or taxon for a given dataset and processes them with the supplied handler. This
   * allows a single query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetBareNames(@Param("datasetKey") Integer datasetKey,
                               @Nullable @Param("sectorKey") Integer sectorKey,
                               ResultHandler<NameUsageWrapper> handler);

  void processTree(@Param("datasetKey") Integer datasetKey,
                   @Param("usageId") String usageId,
                   ResultHandler<NameUsageWrapper> handler);

  NameUsageWrapper get(@Param("datasetKey") int datasetKey,
                       @Param("id") String taxonId);

}
