package org.col.db;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;

public interface Pageable<T> {
  
  List<T> list(@Param("page") Page page);

}
