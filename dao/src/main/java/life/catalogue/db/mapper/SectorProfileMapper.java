package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorProfile;
import life.catalogue.db.CRUD;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface SectorProfileMapper extends CRUD<DSID<Integer>, SectorProfile>, DatasetProcessable<SectorProfile>,
  DatasetPageable<SectorProfile>, CopyDataset {

  /**
   * @return all profiles of a project or release in the order they cascade
   */
  List<SectorProfile> listAll(@Param("datasetKey") int datasetKey);

  /**
   * @return the profiles of the sector's own dataset whose selector matches the sector,
   * in the order they cascade: ascending position, ties broken by id
   */
  List<SectorProfile> listMatching(@Param("key") DSID<Integer> sectorKey);
}
