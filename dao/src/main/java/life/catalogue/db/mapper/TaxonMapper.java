package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * Mapper dealing only with accepted name usages, i.e. Taxon instances.
 *
 * Note that {@link DatasetProcessable#deleteByDataset(int)} needs to also delete all synonyms to not break fk constraints.
 */
public interface TaxonMapper extends CRUD<DSID<String>, Taxon>, DatasetProcessable<Taxon>, DatasetPageable<Taxon> {

  /**
   * Selects a number of distinct taxa from a single dataset by their keys
   *
   * @param ids must contain at least one value, not allowed to be empty !!!
   */
  List<Taxon> listByIds(@Param("datasetKey") int datasetKey, @Param("ids") Set<String> ids);

  int countRoot(@Param("datasetKey") int datasetKey);
  
  List<Taxon> listRoot(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<String> listRootIds(@Param("datasetKey") int datasetKey);

  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("key") DSID<String> key);
  
  /**
   * Same as classification but only returning minimal simple name objects
   * @return list of all parents starting with the immediate parent
   */
  List<SimpleName> classificationSimple(@Param("key") DSID<String> key);
  
  List<TaxonSectorCountMap> classificationCounts(@Param("key") DSID<String> key);
  
  /**
   * @return the sector count map for a given taxon
   */
  TaxonSectorCountMap getCounts(@Param("key") DSID<String> key);
  
  int countChildren(@Param("key") DSID<String> key, @Param("extinct") Boolean inclExtinct);
  
  int countChildrenWithRank(@Param("key") DSID<String> key, @Param("rank") Rank rank, @Param("extinct") Boolean inclExtinct);

  int countChildrenBelowRank(@Param("key") DSID<String> key, @Param("rank") Rank rank, @Param("extinct") Boolean inclExtinct);

  /**
   * Lists all accepted children (taxon) of a given parent
   * @param key the parent to list the direct children from
   * @param rank optional rank cutoff filter to only include children with a rank lower than the one given
   */
  List<Taxon> children(@Param("key") DSID<String> key, @Nullable @Param("rank") Rank rank, @Param("page") Page page);

  /**
   * Recursively updates the sector count for a given taxon and all its parents.
   * @param key the taxon datasetKey & id, pointing to a catalogue or release
   * @param dkey the source datasetKey that sectors are counted for
   * @param delta the change to apply to the count for the given datasetKey, can be negative
   */
  void incDatasetSectorCount(@Param("key") DSID<String> key, @Param("dkey") int dkey, @Param("delta") int delta);
  
  /**
   * Updates a single taxon sector count map
   */
  void updateDatasetSectorCount(@Param("key") DSID<String> key, @Param("count") Int2IntOpenHashMap count);
  
  /**
   * Sets all sector counts above species level to null
   * @param datasetKey
   */
  void resetDatasetSectorCount(@Param("datasetKey") int datasetKey);

}
