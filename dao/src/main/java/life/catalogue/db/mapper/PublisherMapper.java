package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Publisher;
import life.catalogue.api.model.Sector;
import life.catalogue.api.search.SectorSearchRequest;

import life.catalogue.db.CRUD;

import life.catalogue.db.CopyDataset;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import javax.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public interface PublisherMapper extends CRUD<DSID<UUID>, Publisher>, DatasetProcessable<Publisher>, DatasetPageable<Publisher>, CopyDataset {

  List<Publisher> list(@Param("datasetKey") int datasetKey);

}
