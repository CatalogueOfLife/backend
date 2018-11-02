package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.ColUser;

/**
 *
 */
public interface UserMapper extends CRUDMapper<ColUser> {
  
  ColUser getByUsername(@Param("username") String username);

}
