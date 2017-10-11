package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Page;
import org.col.api.Taxon;

import java.util.List;

/**
 *
 */
public interface TaxonMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<Taxon> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  Taxon getByKey(@Param("key") int ikey);

  Taxon get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  void create(Taxon taxon);

}

