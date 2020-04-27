package life.catalogue.db.mapper;

import java.util.List;

import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.CRUD;

/**
 * WARNING!
 * Only Create from CRUD is implemented !!!
 */
public interface NameRelationMapper extends CRUD<DSID<Integer>, NameRelation>, DatasetProcessable<NameRelation> {
  
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
