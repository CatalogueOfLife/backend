package life.catalogue.db;

import life.catalogue.api.model.Page;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface GlobalPageable<T> {
  
  List<T> list(@Param("page") Page page);
  
  int count();
  
}
