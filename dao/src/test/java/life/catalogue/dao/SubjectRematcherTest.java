package life.catalogue.dao;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.db.mapper.DecisionMapper;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.RematchRequest;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.TestDataRule;
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

    int d1 = createDecision(datasetKey,
        new SimpleName("xyz", "Larus fuscus", Rank.SPECIES)
    );
    int d2 = createDecision(datasetKey,
        new SimpleName(null, "Larus fuscus", Rank.SPECIES)
    );
    int d3 = createDecision(datasetKey,
        new SimpleName("null", "Larus", Rank.GENUS)
    );

    SubjectRematcher rem = new SubjectRematcher(PgSetupRule.getSqlSessionFactory(), Datasets.DRAFT_COL, Users.TESTER);
    rem.matchDatasetSubjects(datasetKey);
  
    Sector s1b;
    Sector s2b;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      DecisionMapper dm = session.getMapper(DecisionMapper.class);

      s1b = sm.get(s1);
      assertEquals("root-1", s1b.getSubject().getId());
      assertNull(s1b.getTarget().getId());
  
      s2b = sm.get(s2);
      assertEquals("root-2", s2b.getSubject().getId());
      assertNull(s2b.getTarget().getId());

      // we order decisions in reverse order when searching, so last one gets matched
      EditorialDecision d1b = dm.get(d1);
      assertNull(d1b.getSubject().getId());

      EditorialDecision d2b = dm.get(d2);
      assertEquals("root-2", d2b.getSubject().getId());

      EditorialDecision d3b = dm.get(d3);
      assertNull(d3b.getSubject().getId());
    }
  
    rem.match(RematchRequest.all());

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);

      Sector s1c = sm.get(s1);
      assertEquals("root-1", s1c.getSubject().getId());
      assertEquals("t4", s1c.getTarget().getId());
  
      Sector s2c = sm.get(s2);
      assertEquals("root-2", s2c.getSubject().getId());
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

  static int createDecision(int datasetKey, SimpleName src) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      EditorialDecision d = new EditorialDecision();
      d.setMode(EditorialDecision.Mode.BLOCK);
      d.setDatasetKey(Datasets.DRAFT_COL);
      d.setSubjectDatasetKey(datasetKey);
      d.setSubject(src);
      d.applyUser(TestDataRule.TEST_USER);
      session.getMapper(DecisionMapper.class).create(d);
      return d.getKey();
    }
  }

}