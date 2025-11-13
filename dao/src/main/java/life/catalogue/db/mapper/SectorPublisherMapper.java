package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorPublisher;
import life.catalogue.db.CRUD;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface SectorPublisherMapper extends CRUD<DSID<UUID>, SectorPublisher>, DatasetProcessable<SectorPublisher>, DatasetPageable<SectorPublisher>, CopyDataset {

  List<SectorPublisher> listAll(@Param("datasetKey") int datasetKey);

  /**
   * @param datasetKey a release or project key
   * @return
   */
  Set<UUID> listAllKeys(@Param("datasetKey") int datasetKey);
}
