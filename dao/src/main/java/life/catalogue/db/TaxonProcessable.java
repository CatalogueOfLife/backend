package life.catalogue.db;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.*;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Minimal mapper to deal with entities of type V that are related to a single taxon.
 * @param <T> entity type
 */
public interface TaxonProcessable<T> extends TempNameUsageRelated {

  /**
   * All TaxonProcessable mappers.
   */
  List<Class<? extends TaxonProcessable<?>>> MAPPERS = List.of(
    SynonymMapper.class,
    VerbatimSourceMapper.class,
    VernacularNameMapper.class,
    DistributionMapper.class,
    MediaMapper.class,
    SpeciesInteractionMapper.class,
    TaxonConceptRelationMapper.class,
    TreatmentMapper.class
  );

  List<T> listByTaxon(@Param("key") DSID<String> key);

  int deleteByTaxon(@Param("key") DSID<String> key);

  /**
   * Updates the taxonID of all associated entities that point to the given taxon.
   * @param key the old taxonID to be updated
   * @param newTaxonID
   */
  void updateTaxonID(@Param("key") DSID<String> key, @Param("newTaxonID") String newTaxonID, @Param("userKey") int userKey);
}