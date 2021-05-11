package life.catalogue.dao;

import com.google.common.eventbus.EventBus;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.ExportChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.db.mapper.DatasetExportMapper;

import life.catalogue.db.mapper.DatasetMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class DatasetExportDao extends EntityDao<UUID, DatasetExport, DatasetExportMapper> {
  Set<JobStatus> GOOD = Set.of(JobStatus.FINISHED, JobStatus.WAITING, JobStatus.BLOCKED, JobStatus.RUNNING);
  private final EventBus bus;

  public DatasetExportDao(SqlSessionFactory factory, EventBus bus) {
    super(false, true, factory, DatasetExportMapper.class);
    this.bus = bus;
  }

  public ResultPage<DatasetExport> list(ExportSearchRequest filter, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      DatasetExportMapper mapper = session.getMapper(mapperClass);
      List<DatasetExport> result = mapper.search(filter, page);
      return new ResultPage<>(p, result, () -> mapper.count(filter));
    }
  }

  /**
   * Returns the latest export which matches the request and has not failed or was canceled.
   */
  public DatasetExport current(ExportRequest req) {
    var filter = new ExportSearchRequest(req);
    filter.setStatus(GOOD);

    try (SqlSession session = factory.openSession()) {
      DatasetExportMapper mapper = session.getMapper(mapperClass);
      List<DatasetExport> result = mapper.search(filter, new Page(0,1));
      if (!result.isEmpty()) {
        DatasetExport ex = result.get(0);
        // make sure its based on the same import attempt
        var d = session.getMapper(DatasetMapper.class).get(req.getDatasetKey());
        if (d != null && Objects.equals(d.getImportAttempt(), ex.getImportAttempt())) {
          return ex;
        }
      }
      return null;
    }
  }

  @Override
  protected boolean deleteAfter(UUID key, DatasetExport old, int user, DatasetExportMapper mapper, SqlSession session) {
    // notify event bus
    bus.post(ExportChanged.delete(old));
    return true;
  }

  public void deleteByDataset(int datasetKey, int userKey){
    try (SqlSession session = factory.openSession()) {
      DatasetExportMapper mapper = session.getMapper(mapperClass);
      mapper.processDataset(datasetKey).forEach(exp -> {
        deleteWithSession(exp.getKey(), userKey, session);
      });
    }
  }
}
