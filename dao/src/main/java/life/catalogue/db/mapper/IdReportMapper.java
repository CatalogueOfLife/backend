package life.catalogue.db.mapper;

import life.catalogue.api.model.IdReportEntry;

import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;

/**
 * Mapper to store id reports from the IdProvider so we can reuse them when the release gets published.
 */
public interface IdReportMapper extends DatasetProcessable<IdReportEntry>, Create<IdReportEntry> {

  IdReportEntry get(@Param("datasetKey") int datasetKey, @Param("id") int id);

  /**
   * @return the first created entry of the given id.
   */
  IdReportEntry first(@Param("projectKey") int projectKey, @Param("id") int id);

  /**
   * @return the history of all id report events for a given id.
   */
  List<IdReportEntry> history(@Param("projectKey") int projectKey, @Param("id") int id);

}
