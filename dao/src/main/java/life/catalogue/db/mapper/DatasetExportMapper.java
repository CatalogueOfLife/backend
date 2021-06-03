package life.catalogue.db.mapper;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetProcessable;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface DatasetExportMapper extends CRUD<UUID, DatasetExport>, DatasetProcessable<DatasetExport> {

  List<DatasetExport> search(@Param("req") ExportSearchRequest req, @Param("page") Page page);

  int count(@Param("req") ExportSearchRequest req);

}
