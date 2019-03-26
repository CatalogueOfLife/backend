package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.ColUser;
import org.col.db.CRUDInt;

/**
 *
 */
public interface UserMapper extends CRUDInt<ColUser> {
  
  ColUser getByUsername(@Param("username") String username);
  
}
