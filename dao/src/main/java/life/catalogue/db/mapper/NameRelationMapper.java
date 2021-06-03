package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.*;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface NameRelationMapper extends Create<NameRelation>,
  DatasetProcessable<NameRelation>, SectorProcessable<NameRelation>, NameProcessable<NameRelation>, CopyDataset {
  
  /**
   * Returns the list of name relations for a single name on the other side of the relation (relatedNameId).
   */
  List<NameRelation> listByRelatedName(@Param("key") DSID<String> key);

  /**
   * Returns the list of related names of a given type for a single name on the nameId side of the relation only.
   */
  List<NameRelation> listByType(@Param("key") DSID<String> key, @Param("type") NomRelType type);

  /**
   * Returns the list of related names of a given type for a single name on the relatedNameId side of the relation only.
   */
  List<NameRelation> listByTypeReverse(@Param("key") DSID<String> key, @Param("type") NomRelType type);
}
