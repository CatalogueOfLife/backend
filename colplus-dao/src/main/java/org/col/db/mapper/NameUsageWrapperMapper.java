package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.SimpleName;
import org.col.api.search.NameUsageWrapper;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface  NameUsageWrapperMapper {

  /**
   * Iterates over all taxa with their vernaculars for a given dataset and processes them with the supplied handler. This allows a single
   * query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetUsages(@Param("datasetKey") Integer datasetKey, @Nullable @Param("sectorKey") Integer sectorKey,
                            ResultHandler<NameUsageWrapper> handler);

  /**
   * Iterates over all bare names not linked to a synonym or taxon for a given dataset and processes them with the supplied handler. This
   * allows a single query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetBareNames(@Param("datasetKey") Integer datasetKey, @Nullable @Param("sectorKey") Integer sectorKey,
                               ResultHandler<NameUsageWrapper> handler);

  NameUsageWrapper get(@Param("datasetKey") int datasetKey, @Param("id") String taxonId);

  List<SimpleName> selectClassification(@Param("datasetKey") int datasetKey, @Param("id") String taxonId);

}
