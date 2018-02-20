package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.Taxon;

import java.util.List;

/**
 *
 */
public interface TaxonMapper {

  int count(@Param("datasetKey") Integer datasetKey, @Param("root") Boolean root,
      @Param("nameKey") Integer nameKey);

  List<Taxon> list(@Param("datasetKey") Integer datasetKey, @Param("root") Boolean root,
                   @Param("nameKey") Integer nameKey, @Param("page") Page page);

  Integer lookupKey(@Param("id") String id, @Param("datasetKey") int datasetKey);

  Taxon get(@Param("key") int key);

  /**
   * @return the accepted taxa for a given name key regardless if its a synonym or accepted name
   */
  List<Taxon> accepted(@Param("nkey") int nameKey);

  List<Integer> taxonReferences(@Param("key") int key);

  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("key") int key);

  int countChildren(@Param("key") int key);

  List<Taxon> children(@Param("key") int key, @Param("page") Page page);

  void create(Taxon taxon);

}
