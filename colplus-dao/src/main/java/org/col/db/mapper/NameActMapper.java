package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.NameAct;

/**
 *
 */
public interface NameActMapper {

	NameAct getByKey(@Param("key") int ikey);

	/**
	 * Returns the list of nomenclatural acts for a single name.
	 */
	List<NameAct> listByName(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);

	/**
	 * Returns the list of nomenclatural acts for a whole homotypic group.
	 */
	List<NameAct> listByHomotypicGroup(@Param("datasetKey") int datasetKey,
	    @Param("nameId") String nameId);

	void create(NameAct act);

}
