package life.catalogue.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SpeciesEstimate;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import org.gbif.nameparser.api.Rank;

public interface EstimateMapper extends CRUD<Integer, SpeciesEstimate>, DatasetPageable<SpeciesEstimate>, ProcessableDataset<SpeciesEstimate> {
  
  SpeciesEstimate getById(@Param("id") String id);
  
  /**
   * List all estimates that cannot be linked to target taxa in the catalogue
   * @param datasetKey the catalogues datasetKey
   */
  List<SpeciesEstimate> broken(@Param("datasetKey") int datasetKey);
  
  int searchCount(@Param("datasetKey") int datasetKey,
                  @Nullable @Param("rank") Rank rank,
                  @Nullable @Param("min") Integer min,
                  @Nullable @Param("max") Integer max);
  
  List<SpeciesEstimate> search(@Param("datasetKey") int datasetKey,
                               @Nullable @Param("rank") Rank rank,
                               @Nullable @Param("min") Integer min,
                               @Nullable @Param("max") Integer max,
                               @Param("page") Page page);
  
}
