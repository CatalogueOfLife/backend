package org.col.db.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Reference;

/**
 *
 */
public interface ReferenceMapper {
  
  int count(@Param("datasetKey") int datasetKey);
  
  List<Reference> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<String> listByTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId);
  
  /**
   * Selects a number of distinct references from a single dataset by their keys
   *
   * @param ids must contain at least one value, not allowed to be empty !!!
   */
  List<Reference> listByIds(@Param("datasetKey") int datasetKey, @Param("ids") Set<String> ids);
  
  Reference get(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  void create(Reference name);
  
  /**
   * Links a reference to a taxon
   */
  void linkToTaxon(@Param("datasetKey") int datasetKey, @Param("taxonId") String taxonId, @Param("referenceId") String referenceId);
}
