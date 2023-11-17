package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.model.NameUsageRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

public interface NameRelationMapper extends Create<NameRelation>,
  DatasetProcessable<NameRelation>, SectorProcessable<NameRelation>, NameProcessable<NameRelation>, CopyDataset {
  
  /**
   * Returns the list of name relations for a single name on the other side of the relation (relatedNameId).
   */
  List<NameRelation> listByRelatedName(@Param("key") DSID<String> key);

  /**
   * Returns the list of name relations for a single name, including usage ids.
   */
  List<NameUsageRelation> listUsageRelByName(@Param("key") DSID<String> key);
  /**
   * Returns the list of name relations for a single name on the other side of the relation (relatedNameId),
   * including usages ids.
   */
  List<NameUsageRelation> listUsageRelByRelatedName(@Param("key") DSID<String> key);

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
   * Because of potential db overload by recursions this is limited to a max transitive path depth of 25 - more than enough
   * for any real nomenclatural situations.
   *
   * @param key the name id to start our from
   */
  List<String> listRelatedNameIDs(@Param("key") DSID<String> key, @Param("types") Set<NomRelType> types);

  boolean exists(@Param("datasetKey") int datasetKey, @Param("from") String from, @Param("to") String to, @Param("type") NomRelType type);

  /**
   * Deletes all name relations that have no or broken links to a name or related name.
   * @param datasetKey the datasetKey to restrict the deletion to
   * @param before optional timestamp to restrict deletions to orphans before the given time
   * @return number of deleted name relations
   */
  int deleteOrphans(@Param("datasetKey") int datasetKey, @Param("before") @Nullable LocalDateTime before);
}
