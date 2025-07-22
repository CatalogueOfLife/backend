package life.catalogue.dao;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.db.mapper.DatasetExportMapperTest;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DatasetExportDaoIT extends DaoTestBase {

  @Test
  public void current() {
    DatasetExportDao dao = new DatasetExportDao(new JobConfig(), SqlSessionFactoryRule.getSqlSessionFactory(), validator);
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(TestDataRule.APPLE.key);
    req.setExtinct(true);
    var c = dao.current(req);
    assertNull(c);

    DatasetExport e = DatasetExportMapperTest.create(JobStatus.FINISHED);
    e.setAttempt(null);
    dao.create(e, Users.TESTER);
    commit();

    req = new ExportRequest(e.getRequest().getDatasetKey(), DataFormat.COLDP);
    var prev = dao.current(req);
    assertNull(prev);

    prev = dao.current(e.getRequest());
    assertNotNull(prev);
  }
}