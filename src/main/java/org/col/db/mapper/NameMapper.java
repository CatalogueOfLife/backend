package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Name;

/**
 *
 */
public interface NameMapper {

  Name getByInternalKey(@Param("key") int ikey);

  Name get(@Param("key") String key);

  void insert(Name name);

}

