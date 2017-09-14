package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Serial;

/**
 *
 */
public interface SerialMapper {
  Serial get(@Param("key") int key);

  void insert(Serial serial);

  void update(Serial serial);

  void delete(@Param("key") int key);
}

