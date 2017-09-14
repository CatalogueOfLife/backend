package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Datasource;

public interface DatasourceMapper {

  Datasource get(@Param("key") int key);

  void insert(@Param("d") Datasource datasource);

  void update(@Param("d") Datasource datasource);

  void delete(@Param("key") int key);

}
