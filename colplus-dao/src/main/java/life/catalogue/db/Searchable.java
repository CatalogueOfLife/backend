package life.catalogue.db;

import life.catalogue.api.model.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @param <T> content type
 * @param <P> search request type
 */
public interface Searchable<T, P> {
  
  List<T> search(@Param("req") P request, @Param("page") Page page);
  
  int countSearch(@Param("req") P request);
  
}
