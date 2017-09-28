package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.Name;
import org.col.api.Page;
import org.col.api.PagingResultSet;

import java.util.List;

/**
 *
 */
public interface NameMapper {

  int count(@Param("datasetKey") int datasetKey);

  List<Name> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  Name getByKey(@Param("key") int key);

  Name get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  void create(Name name);

  List<Name> synonyms(@Param("datasetKey") int datasetKey, @Param("id") String id);

  List<Name> synonymsByKey(@Param("key") int key);

  PagingResultSet<Name> search(@Param("datasetKey") int datasetKey, @Param("q") String q);
}

