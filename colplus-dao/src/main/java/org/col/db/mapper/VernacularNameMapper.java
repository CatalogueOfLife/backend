package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Page;
import org.col.api.VernacularName;

import java.util.List;

/**
 *
 */
public interface VernacularNameMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<VernacularName> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  VernacularName getByInternalKey(@Param("key") int ikey);

  VernacularName get(@Param("datasetKey") int datasetKey, @Param("name") String name);

  void create(VernacularName vn);

}

