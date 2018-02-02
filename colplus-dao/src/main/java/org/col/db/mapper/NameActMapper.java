package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.NameAct;

import java.util.List;

/**
 *
 */
public interface NameActMapper {

	NameAct get(@Param("key") int key);

  /**
   * Returns the list of nomenclatural acts for a single name.
   */
  List<NameAct> listByName(@Param("nameKey") int nameKey);

  /**
   * Returns the list of nomenclatural acts for a single reference.
   */
  List<NameAct> listByReference(@Param("referenceKey") int referenceKey);

	/**
	 * Returns the list of nomenclatural acts for a whole homotypic group.
	 */
	List<NameAct> listByHomotypicGroup(@Param("nameKey") int nameKey);

	void create(NameAct act);

}
