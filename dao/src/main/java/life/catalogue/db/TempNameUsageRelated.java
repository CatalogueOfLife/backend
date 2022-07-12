package life.catalogue.db;

import life.catalogue.db.mapper.*;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface TempNameUsageRelated {

  // order matters!
  List<Class<? extends TempNameUsageRelated>> MAPPERS = List.of(
    // usage related
    VerbatimSourceMapper.class,
    DistributionMapper.class,
    MediaMapper.class,
    VernacularNameMapper.class,
    SpeciesInteractionMapper.class,
    TaxonConceptRelationMapper.class,
    TreatmentMapper.class,
    // usage
    NameUsageMapper.class,
    // name related
    NameMatchMapper.class,
    TypeMaterialMapper.class,
    NameRelationMapper.class,
    // name
    NameMapper.class
  );

  /**
   * Deletes all that are found in the temp name usage table (see NameUsageMapper).
   */
  int deleteByTemp(@Param("datasetKey") int datasetKey);

}