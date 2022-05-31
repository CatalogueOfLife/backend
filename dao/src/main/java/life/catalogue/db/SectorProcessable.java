package life.catalogue.db;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.*;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * Minimal mapper to deal with entities of type V for an entire sector in a managed dataset.
 * @param <V> entity type
 */
public interface SectorProcessable<V> {

  /**
   * All sector processable mappers in an order suited for deletions.
   */
  List<Class<? extends SectorProcessable<?>>> MAPPERS = List.of(
    // usage related
    VerbatimSourceMapper.class,
    VernacularNameMapper.class,
    DistributionMapper.class,
    MediaMapper.class,
    SpeciesInteractionMapper.class,
    TaxonConceptRelationMapper.class,
    TreatmentMapper.class,
    // usage
    NameUsageMapper.class,
    // name related
    TypeMaterialMapper.class,
    NameRelationMapper.class,
    NameMatchMapper.class,
    // name
    NameMapper.class,
    // refs
    ReferenceMapper.class
  );

  /**
   * Iterates over all entities of a given sector in a memory friendly way, bypassing the 1st level mybatis cache.
   */
  Cursor<V> processSector(@Param("key") DSID<Integer> sectorKey);

  /**
   * Deletes all entities from the given sector
   * @param sectorKey sector key of the entities to be deleted
   */
  int deleteBySector(@Param("key") DSID<Integer> sectorKey);

  /**
   * Updates all entities for the given sector and sets their sectorKey to NULL
   * @param sectorKey sector key of the entities to be deleted
   */
  int removeSectorKey(@Param("key") DSID<Integer> sectorKey);

}