package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.BareName;
import org.col.api.model.NameUsage;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.search.NameUsageWrapper;

/**
 * Mapper dealing with methods returning the NameUsage interface,
 * i.e. a name in the context of either a Taxon, TaxonVernacularUsage, Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible
 * avoiding duplication.
 */
public interface NameUsageMapper {
  
  /**
   * Iterates over all taxa with their vernaculars for a given dataset
   * and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetTaxa(@Param("datasetKey") int datasetKey, ResultHandler<NameUsageWrapper<NameUsage>> handler);
  
  /**
   * Iterates over all synonyms for a given dataset
   * and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetSynonyms(@Param("datasetKey") int datasetKey, ResultHandler<NameUsageWrapper<NameUsage>> handler);
  
  /**
   * Iterates over all bare names not linked to a synonym or taxon for a given dataset
   * and processes them with the supplied handler.
   * This allows a single query to efficiently stream all its values without keeping them in memory.
   */
  void processDatasetBareNames(@Param("datasetKey") int datasetKey, ResultHandler<NameUsageWrapper<NameUsage>> handler);
}
