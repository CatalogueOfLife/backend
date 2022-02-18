package life.catalogue.db.mapper;

import life.catalogue.api.model.IdReportEntry;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;

/**
 * Mapper to store id reports from the IdProvider so we can reuse them when the release gets published.
 */
public interface IdReportMapper {

  /**
   * Creates a new report entry for the given release.
   */
  void create(IdReportEntry id);

  IdReportEntry get(@Param("datasetKey") int datasetKey, @Param("id") int id);

  Cursor<IdReportEntry> processDataset(@Param("datasetKey") int datasetKey);

  int deleteDataset(@Param("datasetKey") int datasetKey);

  List<IdReportEntry> history(@Param("projectKey") int projectKey, @Param("id") int id);

}
