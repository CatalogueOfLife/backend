package life.catalogue.db;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.*;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Entities attached to the usages of a sector that the sector does not own: a merge sector adds vernacular names to
 * usages of other sectors and editors add any extension to any usage. Their foreign keys to name_usage are enforced,
 * so they keep a sector from deleting its usages, which every sync does. A relation counts if either of its ends is
 * a usage of the sector. See https://github.com/CatalogueOfLife/backend/issues/1585
 *
 * @param <T> entity type, carrying the ids of the usages it is attached to
 */
public interface SectorForeignProcessable<T> {

  /**
   * All mappers whose entities have a foreign key to name_usage.
   */
  List<Class<? extends SectorForeignProcessable<?>>> MAPPERS = List.of(
    VernacularNameMapper.class,
    DistributionMapper.class,
    MediaMapper.class,
    TaxonPropertyMapper.class,
    TreatmentMapper.class,
    SpeciesInteractionMapper.class,
    TaxonConceptRelationMapper.class
  );

  /**
   * Lists the entities of other sectors or of none that are attached to usages of the given sector.
   * @param sectorKey the sector key
   */
  List<T> listForeignBySector(@Param("key") DSID<Integer> sectorKey);

  /**
   * Deletes the entities listed by {@link #listForeignBySector}.
   * @param sectorKey the sector key
   * @return number of deleted entities
   */
  int deleteForeignBySector(@Param("key") DSID<Integer> sectorKey);

}
