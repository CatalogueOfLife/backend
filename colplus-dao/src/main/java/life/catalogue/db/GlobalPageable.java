package life.catalogue.db;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.Page;

public interface GlobalPageable<T> {
  
  List<T> list(@Param("page") Page page);
  
  int count();
  
}
