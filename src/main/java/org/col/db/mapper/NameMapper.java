package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Name;
import org.col.api.PagingResult;

import java.util.List;

/**
 *
 */
public interface NameMapper {

  Name getByInternalKey(@Param("key") int key);

  Name get(@Param("dkey") int datasetKey, @Param("key") String key);

  void insert(Name name);

  List<Name> synonyms(@Param("dkey") int datasetKey, @Param("key") String key);

  List<Name> synonymsByInternalKey(@Param("key") int key);

  PagingResult<Name> search(@Param("dkey") int datasetKey, @Param("q") String q);
}

