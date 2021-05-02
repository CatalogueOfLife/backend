package life.catalogue.db;

import life.catalogue.api.model.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GlobalPageable<T> {
  
  List<T> list(@Param("page") Page page);
  
  int count();
  
}
