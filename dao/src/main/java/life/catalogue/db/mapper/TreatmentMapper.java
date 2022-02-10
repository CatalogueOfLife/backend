package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Treatment;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.TempNameUsageRelated;

import org.apache.ibatis.annotations.Param;

public interface TreatmentMapper extends CopyDataset, SectorProcessable<Treatment>, TempNameUsageRelated {

  Treatment get(@Param("key") DSID<String> taxonID);

  /**
   * referenceId is ignored as it is stored in Taxon.accordingToId
   */
  void create(@Param("obj") Treatment treatment);

  int deleteByTaxon(@Param("key") DSID<String> key);

}
