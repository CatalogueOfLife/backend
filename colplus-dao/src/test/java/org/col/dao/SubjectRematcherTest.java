package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.RematchRequest;
import org.col.api.model.Sector;
import org.col.api.model.SimpleName;
import org.col.api.vocab.Users;
import org.col.db.MybatisTestUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.col.db.mapper.SectorMapper;
import org.gbif.nameparser.api.Rank;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

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
    int s2 = createSector(Sector.Mode.MERGE, datasetKey,
        new SimpleName(null, "Larus fuscus", Rank.SPECIES),
        new SimpleName(null, "Lepidoptera", Rank.ORDER)
    );
  
    SubjectRematcher rem = new SubjectRematcher(PgSetupRule.getSqlSessionFactory(), Users.TESTER);
    rem.matchDatasetSubjects(datasetKey);
  
    Sector s1b;
    Sector s2b;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      s1b = sm.get(s1);
      Assert.assertNotNull(s1b.getSubject().getId());
      Assert.assertNull(s1b.getTarget().getId());
  
      s2b = sm.get(s2);
      Assert.assertNotNull(s2b.getSubject().getId());
      Assert.assertNull(s2b.getTarget().getId());
    }
  
    rem.match(RematchRequest.all());

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);

      Sector s1c = sm.get(s1);
      Assert.assertNull(s1c.getSubject().getId());
      Assert.assertEquals("t4", s1c.getTarget().getId());
  
      Sector s2c = sm.get(s2);
      Assert.assertNull(s2c.getSubject().getId());
      Assert.assertEquals("t5", s2c.getTarget().getId());
    }
  }
  
  static int createSector(Sector.Mode mode, int datasetKey, SimpleName src, SimpleName target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.applyUser(TestDataRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector.getKey();
    }
  }
}