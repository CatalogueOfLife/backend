package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.ColUser;
import life.catalogue.db.GlobalPageable;

/**
 *
 */
public interface UserMapper extends GlobalPageable<ColUser> {
  
  ColUser getByUsername(@Param("username") String username);
  
  void create(ColUser obj);
  
  ColUser get(@Param("key") int key);
  
  int update(ColUser obj);
  
  int delete(@Param("key") int key);
}
