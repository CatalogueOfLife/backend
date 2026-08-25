package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.file.Files;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FileMetricsSectorDaoTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.draftWithSectors();

  @Test
  public void noFileWrittenWhenNoNames() throws Exception {
    File repo = Files.createTempDirectory("clb-metrics").toFile();
    repo.deleteOnExit();
    var dao = new FileMetricsSectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), repo);

    // sector 1 of the COL project has no usages in this fixture, so no name strings
    DSID<Integer> key = DSID.of(Datasets.COL, 1);
    dao.updateNames(key, key, 1);

    assertFalse("an empty names file must not be created", dao.namesFile(key, 1).exists());

    // a real sync always leaves a sector_import metrics row behind, even one with a zero name
    // count - the fixture does not ship one, so record it here the way the sync would have,
    // otherwise getNames has no way to tell "empty" apart from "unknown attempt"
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorImport si = new SectorImport();
      si.setDatasetKey(key.getDatasetKey());
      si.setSectorKey(key.getId());
      si.setAttempt(1);
      si.setCreatedBy(TestDataRule.TEST_USER.getKey());
      si.setNameCount(0);
      session.getMapper(SectorImportMapper.class).create(si);
    }

    // and reading it back must not blow up
    assertEquals(0, dao.getNames(key, 1).count());
  }

  /**
   * A missing file with no sector import row behind it is still a genuine NotFound - the tolerance
   * must not swallow an unknown attempt.
   */
  @Test(expected = FileMetricsDao.AttemptMissingException.class)
  public void unknownAttemptStill404s() throws Exception {
    File repo = Files.createTempDirectory("clb-metrics").toFile();
    repo.deleteOnExit();
    var dao = new FileMetricsSectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), repo);
    dao.getNames(DSID.of(Datasets.COL, 1), 999).count();
  }
}
