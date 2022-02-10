package life.catalogue.dao;

import life.catalogue.api.event.ExportChanged;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.db.mapper.DatasetExportMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.validation.Validator;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.eventbus.EventBus;

public class DatasetExportDao extends EntityDao<UUID, DatasetExport, DatasetExportMapper> {
  Set<JobStatus> GOOD = Set.of(JobStatus.FINISHED, JobStatus.WAITING, JobStatus.BLOCKED, JobStatus.RUNNING);
  private final EventBus bus;
  private final File exportDir;

  public DatasetExportDao(File exportDir, SqlSessionFactory factory, EventBus bus, Validator validator) {
    super(false, true, factory, DatasetExport.class, DatasetExportMapper.class, validator);
    this.bus = bus;
    this.exportDir = exportDir;
  }

  public File downloadFile(UUID key) {
    return new File(exportDir, DatasetExport.downloadFilePath(key));
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
        if (d != null && Objects.equals(d.getAttempt(), ex.getAttempt())) {
          return ex;
        }
      }
      return null;
    }
  }

  @Override
  protected boolean deleteAfter(UUID key, DatasetExport old, int user, DatasetExportMapper mapper, SqlSession session) {
    // remove exported file
    File zip = downloadFile(key);
    if (zip.exists()) {
      FileUtils.deleteQuietly(zip);
    }
    // notify event bus
    bus.post(ExportChanged.deleted(old));
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
