package life.catalogue.db;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Entity;
import org.apache.ibatis.annotations.Param;

public interface CRUD<K, V extends Entity<K>> extends Create<V> {

  V get(@Param("key") K key);

  default V getOrThrow(@Param("key") K key, Class<V> entity) throws NotFoundException {
    V obj = get(key);
    if (obj == null) {
      throw NotFoundException.notFound(entity, key);
    }
    return obj;
  }
  
  int update(V obj);
  
  int delete(@Param("key") K key);

}
