package life.catalogue.db.mapper;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Page;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

public interface DatasetExportMapper extends CRUD<UUID, DatasetExport>, DatasetProcessable<DatasetExport> {

  List<DatasetExport> search(@Param("req") DatasetExport.Search req, @Param("page") Page page);

  int count(@Param("req") DatasetExport.Search req);

}
