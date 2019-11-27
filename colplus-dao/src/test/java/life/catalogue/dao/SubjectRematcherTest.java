package life.catalogue.dao;

import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.RematchRequest;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TestDataRule;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class SubjectRematcherTest {
  
  @ClassRule
  public static PgSetupRule pg = new PgSetupRule();
  
  @Rule
  public final TestDataRule importRule = TestDataRule.apple();

  @Test
  public void matchDataset() {
    /*
    Name n1 = draftName(nm, datasetKey,"n1", "Animalia", Rank.KINGDOM);
    Name n2 = draftName(nm, datasetKey,"n2", "Arthropoda", Rank.KINGDOM);
    Name n3 = draftName(nm, datasetKey,"n3", "Insecta", Rank.CLASS);
    Name n4 = draftName(nm, datasetKey,"n4", "Coleoptera", Rank.ORDER);
    Name n5 = draftName(nm, datasetKey,"n5", "Lepidoptera", Rank.ORDER);
     */
    MybatisTestUtils.populateDraftTree(importRule.getSqlSession());
    final int datasetKey = 11;
    
    int s1 = createSector(Sector.Mode.ATTACH, datasetKey,
        new SimpleName(null, "Malus sylvestris", Rank.SPECIES),
        new SimpleName(null, "Coleoptera", Rank.ORDER)
    );
    int s2 = createSector(Sector.Mode.UNION, datasetKey,
        new SimpleName(null, "Larus fuscus", Rank.SPECIES),
        new SimpleName(null, "Lepidoptera", Rank.ORDER)
    );
  
    SubjectRematcher rem = new SubjectRematcher(PgSetupRule.getSqlSessionFactory(), Datasets.DRAFT_COL, Users.TESTER);
    rem.matchDatasetSubjects(datasetKey);
  
    Sector s1b;
    Sector s2b;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      s1b = sm.get(s1);
      assertNotNull(s1b.getSubject().getId());
      assertNull(s1b.getTarget().getId());
  
      s2b = sm.get(s2);
      assertNotNull(s2b.getSubject().getId());
      assertNull(s2b.getTarget().getId());
    }
  
    rem.match(RematchRequest.all());

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);

      Sector s1c = sm.get(s1);
      assertNull(s1c.getSubject().getId());
      assertEquals("t4", s1c.getTarget().getId());
  
      Sector s2c = sm.get(s2);
      assertNull(s2c.getSubject().getId());
      assertEquals("t5", s2c.getTarget().getId());
    }
  }
  
  static int createSector(Sector.Mode mode, int datasetKey, SimpleName src, SimpleName target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(Datasets.DRAFT_COL);
      sector.setSubjectDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.applyUser(TestDataRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector.getKey();
    }
  }
}