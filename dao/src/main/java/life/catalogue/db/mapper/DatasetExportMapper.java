package life.catalogue.db.mapper;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.model.Page;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

public interface DatasetExportMapper extends CRUD<UUID, DatasetExport>, DatasetProcessable<DatasetExport> {

  List<DatasetExport> search(@Param("req") ExportSearchRequest req, @Param("page") Page page);

  int count(@Param("req") ExportSearchRequest req);

}
