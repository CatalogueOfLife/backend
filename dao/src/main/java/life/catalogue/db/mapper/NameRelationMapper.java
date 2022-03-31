package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.*;

import java.util.List;
import java.util.Set;

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

  /**
   * Returns the unique list of ids from all related names within the same dataset that are (transitively) connected by relations of the given types.
   * The starting key is excluded from the result.
   *
   * Because of potential db overload by recursions this is limited to a max transitive path depth of 100 - more than enough
   * for any real nomenclatural situations.
   *
   * @param key the name id to start our from
   */
  List<String> listRelatedNameIDs(@Param("key") DSID<String> key, @Param("types") Set<NomRelType> types);
}
