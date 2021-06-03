package life.catalogue.db;

import life.catalogue.api.model.Page;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * @param <T> content type
 * @param <P> search request type
 */
public interface Searchable<T, P> {

  Cursor<T> processSearch(@Param("req") P request);

  List<T> search(@Param("req") P request, @Param("page") Page page);
  
  int countSearch(@Param("req") P request);
  
}
