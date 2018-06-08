package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.NameRelation;

/**
 *
 */
public interface NameRelationMapper {

  /**
   * Returns the list of name relations for a single name,
	 * regardless which side of the act relation the name is on.
   */
  List<NameRelation> list(@Param("nameKey") int nameKey);

	void create(NameRelation rel);

}
