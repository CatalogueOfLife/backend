package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Publisher;
import life.catalogue.db.CRUD;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface PublisherMapper extends CRUD<DSID<UUID>, Publisher>, DatasetProcessable<Publisher>, DatasetPageable<Publisher>, CopyDataset {

  List<Publisher> listAll(@Param("datasetKey") int datasetKey);

}
