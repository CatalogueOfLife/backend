package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Sector;
import org.col.api.model.SimpleName;
import org.col.db.MybatisTestUtils;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.col.db.mapper.SectorMapper;
import org.gbif.nameparser.api.Rank;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class DecisionRematcherTest {
  
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
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      DecisionRematcher rem = new DecisionRematcher(session);
      
      rem.matchDatasetSubjects(datasetKey);

      Sector s1b = sm.get(s1);
      Assert.assertNotNull(s1b.getSubject().getId());
      Assert.assertNull(s1b.getTarget().getId());

      Sector s2b = sm.get(s2);
      Assert.assertNotNull(s2b.getSubject().getId());
      Assert.assertNull(s2b.getTarget().getId());
  
      rem.matchBrokenSectorTargets();
      Sector s1c = sm.get(s1);
      Assert.assertEquals(s1b.getSubject().getId(), s1c.getSubject().getId());
      Assert.assertNotNull(s1c.getTarget().getId());
  
      Sector s2c = sm.get(s2);
      Assert.assertEquals(s2b.getSubject().getId(), s2c.getSubject().getId());
      Assert.assertNotNull(s2c.getTarget().getId());
  
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