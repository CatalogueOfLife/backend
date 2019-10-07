package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DSID;
import org.col.api.model.NameRelation;
import org.col.api.vocab.NomRelType;
import org.col.db.CRUD;

/**
 * WARNING!
 * Only Create from CRUD is implemented !!!
 */
public interface NameRelationMapper extends CRUD<DSID<Integer>, NameRelation>, ProcessableDataset<NameRelation> {
  
  /**
   * Returns the list of name relations for a single name,
   * regardless which side of the act relation the name is on.
   */
  List<NameRelation> list(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  /**
   * Returns the list of related names of a given type for a single name on the nameId side of the relation only.
   */
  List<NameRelation> listByType(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId, @Param("type") NomRelType type);
  
}
