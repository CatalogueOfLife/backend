package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.SpeciesEstimate;

public interface EstimateMapper extends GlobalCRUDMapper<SpeciesEstimate> {
  
  SpeciesEstimate getById(@Param("id") String id);
  
  /**
   * List all estimates that cannot be linked to subject taxa in the catalogue
   */
  List<SpeciesEstimate> broken();
  
}
