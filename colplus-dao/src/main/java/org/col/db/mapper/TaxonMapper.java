package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Page;
import org.col.api.Taxon;

import java.util.List;

/**
 *
 */
public interface TaxonMapper {

  Integer lookupKey(@Param("datasetKey") int datasetKey, @Param("id") String id);

  int count(@Param("datasetKey") int datasetKey);

  List<Taxon> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  Taxon get(@Param("key") int key);

  List<Taxon> classification(@Param("key") int key);

  List<Taxon> children(@Param("key") int key, @Param("page") Page page);

  void create(Taxon taxon);

}

