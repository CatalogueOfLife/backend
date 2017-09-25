package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Taxon;

/**
 *
 */
public interface TaxonMapper {

  Taxon getByInternalKey(@Param("key") int ikey);

  Taxon get(@Param("dkey") int datasetKey, @Param("key") String key);

  void insert(Taxon taxon);

}

