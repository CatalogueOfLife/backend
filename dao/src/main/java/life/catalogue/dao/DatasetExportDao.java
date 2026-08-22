package life.catalogue.dao;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.ExportSearchRequest;
import life.catalogue.api.vocab.JobPriority;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetExportMapper;
import life.catalogue.db.mapper.JobMapper;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;

public class DatasetExportDao extends EntityDao<UUID, DatasetExport, DatasetExportMapper> {
  Set<JobStatus> GOOD = Set.of(JobStatus.FINISHED, JobStatus.WAITING, JobStatus.BLOCKED, JobStatus.RUNNING);
  private final JobConfig cfg;

  public DatasetExportDao(JobConfig cfg, SqlSessionFactory factory, Validator validator) {
    super(false, true, factory, DatasetExport.class, DatasetExportMapper.class, validator);
    this.cfg = cfg;
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
   * Returns the latest export which matches the request and has not failed or was canceled or deleted.
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

  /**
   * Exports created directly via this dao instead of through the job executor still need a job record,
   * so their generic lifecycle (status, timestamps, user, result file) is queryable via the key join.
   * The executor driven export flow inserts the export via the mapper and creates the job itself, so it never gets here.
   */
  @Override
  protected boolean createAfter(DatasetExport obj, int user, DatasetExportMapper mapper, SqlSession session) {
    JobMapper jm = session.getMapper(JobMapper.class);
    if (jm.get(obj.getKey()) == null) {
      JobInfo j = new JobInfo();
      j.setKey(obj.getKey());
      j.setJob("DatasetExportJob");
      j.setStatus(obj.getStatus() == null ? JobStatus.FINISHED : obj.getStatus());
      j.setPriority(JobPriority.LOW);
      j.setDatasetKey(obj.getRequest() == null ? null : obj.getRequest().getDatasetKey());
      j.setCreatedBy(obj.getCreatedBy() == null ? user : obj.getCreatedBy());
      j.setCreated(obj.getCreated() == null ? LocalDateTime.now() : obj.getCreated());
      j.setStarted(obj.getStarted());
      j.setFinished(obj.getFinished());
      j.setError(obj.getError());
      j.setResultMd5(obj.getMd5());
      j.setResultSize(obj.getSize());
      jm.create(j);
    }
    return true;
  }

  @Override
  protected boolean deleteAfter(UUID key, DatasetExport old, int user, DatasetExportMapper mapper, SqlSession session) {
    // remove exported file
    File zip = cfg.downloadFile(key);
    if (zip.exists()) {
      FileUtils.deleteQuietly(zip);
    }
    return true;
  }

  public void deleteByDataset(final int datasetKey, int userKey){
    try (SqlSession session = factory.openSession()) {
      final DatasetExportMapper mapper = session.getMapper(mapperClass);
      PgUtils.consume(()->mapper.processDataset(datasetKey), exp -> deleteWithSession(exp.getKey(), userKey, session));
    }
  }
}
