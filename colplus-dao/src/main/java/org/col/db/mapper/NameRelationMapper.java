package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.NameRelation;
import org.col.api.vocab.NomRelType;

/**
 *
 */
public interface NameRelationMapper {

  /**
   * Returns the list of name relations for a single name,
	 * regardless which side of the act relation the name is on.
   */
  List<NameRelation> list(@Param("nameKey") int nameKey);

  /**
   * Returns the list of related names of a given type for a single name on the nameKey side of the relation only.
   */
  List<NameRelation> listByType(@Param("nameKey") int nameKey, @Param("type") NomRelType type);

  void create(NameRelation rel);

}
