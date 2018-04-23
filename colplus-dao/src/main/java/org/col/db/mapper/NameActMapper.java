package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.NameAct;

import java.util.List;

/**
 *
 */
public interface NameActMapper {

  /**
   * Returns the list of nomenclatural acts for a single name,
	 * regardless which side of the act relation the name is on.
   */
  List<NameAct> list(@Param("nameKey") int nameKey);

	void create(NameAct act);

}
