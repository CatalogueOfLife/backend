package life.catalogue.importer;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.GbifRegistration;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ImporterConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.GbifRegistrationMapper;
import life.catalogue.db.mapper.OutboxMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.outbox.Outbox;

import java.util.UUID;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
    PgImport imp = new PgImport(1, DOI.test(RandomUtils.randomLatinString(20)), ds, Users.TESTER, null,
      SqlSessionFactoryRule.getSqlSessionFactory(), new ImporterConfig(), ddao, null, null, null);
    imp.updateMetadata();
  }

  /**
   * A dataset registered in GBIF from ChecklistBank serves the archive of a new import straight away,
   * so GBIF is asked to crawl it again, even if the dataset's metadata is locked and no dataset update happens.
   */
  @Test
  public void gbifRegistered() throws Exception {
    Dataset d = TestEntityGenerator.setUser(TestEntityGenerator.newDataset("registered"));
    var settings = new DatasetSettings();
    settings.enable(Setting.LOCK_METADATA);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(DatasetMapper.class).create(d);
      session.getMapper(GbifRegistrationMapper.class).create(new GbifRegistration(d.getKey(), UUID.randomUUID(), Users.TESTER));
    }

    PgImport imp = new PgImport(1, DOI.test(RandomUtils.randomLatinString(20)), new DatasetWithSettings(d, settings), Users.TESTER,
      null, SqlSessionFactoryRule.getSqlSessionFactory(), new ImporterConfig(), ddao, null, null, null);
    imp.updateMetadata();

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var cmd = session.getMapper(OutboxMapper.class).getByKey(OutboxType.GBIF_REGISTRY, d.getKey().toString());
      assertNotNull(cmd);
      assertTrue(Outbox.gbifPushMetadata(cmd.getParams()));
    }
  }

  /**
   * A dataset synced from GBIF gets its EML uploaded to GBIF after an import, if ChecklistBank writes back to GBIF.
   */
  @Test
  public void gbifSynced() throws Exception {
    Dataset d = TestEntityGenerator.setUser(TestEntityGenerator.newDataset("synced"));
    d.setPrivat(false);
    d.setGbifKey(UUID.randomUUID());
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(DatasetMapper.class).create(d);
    }
    Mockito.when(ddao.isGbifBidirectional()).thenReturn(true);

    PgImport imp = new PgImport(1, DOI.test(RandomUtils.randomLatinString(20)), new DatasetWithSettings(d, new DatasetSettings()),
      Users.TESTER, null, SqlSessionFactoryRule.getSqlSessionFactory(), new ImporterConfig(), ddao, null, null, null);
    imp.updateMetadata();

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var cmd = session.getMapper(OutboxMapper.class).getByKey(OutboxType.GBIF_REGISTRY, d.getKey().toString());
      assertNotNull(cmd);
      assertTrue(Outbox.gbifPushMetadata(cmd.getParams()));
    }
  }

}
