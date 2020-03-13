package life.catalogue.db;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.DataEntity;

public interface CRUD<K, V extends DataEntity<K>> {

  void create(V obj);

  V get(@Param("key") K key);
  
  int update(V obj);
  
  int delete(@Param("key") K key);

}
