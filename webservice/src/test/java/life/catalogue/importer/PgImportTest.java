package life.catalogue.importer;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ImporterConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 */
public class PgImportTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  AtomicInteger cnt = new AtomicInteger(0);
  DatasetDao ddao = Mockito.mock(DatasetDao.class);

  @Test
  public void duplicateAlias() throws Exception {
    Dataset d = TestEntityGenerator.setUser(TestEntityGenerator.newDataset("first"));
    d.setAlias("first");

    Dataset d2 = TestEntityGenerator.setUser(TestEntityGenerator.newDataset("second"));
    d2.setAlias("second");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.create(d);
      dm.create(d2);
    }

    DatasetWithSettings ds = new DatasetWithSettings(d2, new DatasetSettings());
    d2.setAlias(d.getAlias());
    PgImport imp = new PgImport(1, ds, Users.TESTER, null,
      SqlSessionFactoryRule.getSqlSessionFactory(), new ImporterConfig(), ddao, null);
    imp.updateMetadata();
  }

}
