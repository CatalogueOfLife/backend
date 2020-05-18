package life.catalogue.db;

import life.catalogue.api.model.Entity;
import org.apache.ibatis.annotations.Param;

public interface CRUD<K, V extends Entity<K>> extends Create<V> {

  V get(@Param("key") K key);
  
  int update(V obj);
  
  int delete(@Param("key") K key);

}
