package life.catalogue.db;

import java.util.List;

import life.catalogue.api.model.Page;
import org.apache.ibatis.annotations.Param;

public interface Searchable<T, P> {
  
  List<T> search(@Param("req") P request, @Param("page") Page page);
  
  int countSearch(@Param("req") P request);
  
}
