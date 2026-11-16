package life.catalogue.jobs;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ImportFormatBackfillJobIT {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  NormalizerConfig cfg;
  final int key = DATASET11.getKey();

  @Before
  public void init() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = tmp.newFolder("archives");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var settings = Optional.ofNullable(dm.getSettings(key)).orElseGet(DatasetSettings::new);
      settings.put(Setting.DATA_FORMAT, DataFormat.DWCA);
      dm.updateSettings(key, settings, Users.TESTER);
    }
  }

  private int create(String md5, DataFormat format) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var di = new DatasetImport();
      di.setDatasetKey(key);
      di.setJobKey(UUID.randomUUID());
      di.setJob("ImportJob");
      di.setStatus(JobStatus.FINISHED);
      di.setCreatedBy(Users.TESTER);
      di.setStarted(LocalDateTime.now());
      di.setFinished(LocalDateTime.now());
      di.setOrigin(DatasetOrigin.EXTERNAL);
      di.setMd5(md5);
      di.setFormat(format);
      MapperTestBase.createJob(session, di);
      session.getMapper(DatasetImportMapper.class).create(di);
      return di.getAttempt();
    }
  }

  private void archive(int attempt, String fixture) throws Exception {
    File archive = cfg.archive(key, attempt);
    archive.getParentFile().mkdirs();
    CompressionUtil.zipDir(new File("src/test/resources/" + fixture), archive, true);
  }

  private DataFormat format(int attempt) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      return session.getMapper(DatasetImportMapper.class).get(key, attempt).getFormat();
    }
  }

  @Test
  public void backfill() throws Exception {
    int early = create("m1", null);     // no archive stored, same source as the next one
    int coldp = create("m1", null);     // ColDP archive although the dataset is configured as DwC-A now
    int lost = create("m2", null);      // no archive and no other import of its source
    int dwca = create("m3", null);
    int recorded = create("m4", DataFormat.ACEF);
    archive(coldp, "coldp/1");
    archive(dwca, "dwca/0");

    // a dry run changes nothing
    new ImportFormatBackfillJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, true).execute();
    assertNull(format(early));
    assertNull(format(coldp));
    assertNull(format(dwca));

    new ImportFormatBackfillJob(Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, false).execute();
    assertEquals(DataFormat.COLDP, format(coldp));
    assertEquals(DataFormat.COLDP, format(early));
    assertEquals(DataFormat.DWCA, format(dwca));
    assertEquals(DataFormat.ACEF, format(recorded));
    assertNull(format(lost));
  }
}
