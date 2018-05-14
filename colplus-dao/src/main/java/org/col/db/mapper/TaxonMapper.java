package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.Taxon;

/**
 *
 */
public interface TaxonMapper {

  int count(@Param("datasetKey") Integer datasetKey, @Param("root") Boolean root);

  List<Taxon> list(@Param("datasetKey") Integer datasetKey, @Param("root") Boolean root, @Param("page") Page page);

  Integer lookupKey(@Param("id") String id, @Param("datasetKey") int datasetKey);

  Taxon get(@Param("key") int key);

  /**
   * Warning, the name property is not set cause it is expected to exist already
   */
  List<Taxon> getByName(@Param("name") Name name);

  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("key") int key);

  int countChildren(@Param("key") int key);

  List<Taxon> children(@Param("key") int key, @Param("page") Page page);

  /**
   * Creates a new taxon linked to a given name.
   * Note that the name must exist already and taxon.name.key must exist.
   * @param taxon
   */
  void create(Taxon taxon);

}
