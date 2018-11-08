package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Taxon;

/**
 *
 */
public interface TaxonMapper {
  
  int count(@Param("datasetKey") int datasetKey, @Param("root") boolean root);
  
  List<Taxon> list(@Param("datasetKey") int datasetKey, @Param("root") boolean root, @Param("page") Page page);
  
  List<Taxon> listByName(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  Taxon get(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  int countChildren(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  List<Taxon> children(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("page") Page page);
  
  /**
   * Creates a new taxon linked to a given name.
   * Note that the name must exist already and taxon.name.key must exist.
   *
   * @param taxon
   */
  void create(Taxon taxon);
  
}
