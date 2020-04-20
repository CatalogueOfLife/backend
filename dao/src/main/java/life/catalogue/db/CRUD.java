package life.catalogue.db;

import life.catalogue.api.model.Entity;
import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.DataEntity;

public interface CRUD<K, V extends Entity<K>> {

  void create(V obj);

  V get(@Param("key") K key);
  
  int update(V obj);
  
  int delete(@Param("key") K key);

}
