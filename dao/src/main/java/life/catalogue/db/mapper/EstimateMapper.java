package life.catalogue.db.mapper;

import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.api.search.EstimateSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.Searchable;
import org.apache.ibatis.annotations.Param;

public interface EstimateMapper extends CRUD<Integer, SpeciesEstimate>, DatasetPageable<SpeciesEstimate>,
    ProcessableDataset<SpeciesEstimate>, Searchable<SpeciesEstimate, EstimateSearchRequest> {
  
  SpeciesEstimate getById(@Param("id") String id);
  
}
