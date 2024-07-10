package life.catalogue.matching.decision;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.gbif.nameparser.api.Rank;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SectorRematcherTest {

  static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  NameDao nDao;
  TaxonDao tDao;
  SectorDao dao;

  @ClassRule
  public static PgSetupRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule importRule = TestDataRule.apple();

  @Before
  public void init(){
    nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    dao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tDao, validator);
  }

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

    DSID<Integer> s1 = createSector(Sector.Mode.ATTACH, datasetKey,
      SimpleNameLink.of("Malus sylvestris", Rank.SPECIES),
      SimpleNameLink.of("Coleoptera", Rank.ORDER)
    );
    DSID<Integer> s2 = createSector(Sector.Mode.UNION, datasetKey,
      SimpleNameLink.of("Larus fuscus", Rank.SPECIES),
      SimpleNameLink.of("Lepidoptera", Rank.ORDER)
    );

    SectorRematchRequest req = new SectorRematchRequest(Datasets.COL, false);
    req.setSubjectDatasetKey(datasetKey);
    req.setTarget(false);
    SectorRematcher.match(dao, req, Users.TESTER);

    Sector s1b;
    Sector s2b;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);

      s1b = sm.get(s1);
      assertEquals("root-1", s1b.getSubject().getId());
      assertNull(s1b.getTarget().getId());

      s2b = sm.get(s2);
      assertEquals("root-2", s2b.getSubject().getId());
      assertNull(s2b.getTarget().getId());
    }

    req.setSubjectDatasetKey(null);
    req.setTarget(true);
    SectorRematcher.match(dao, req, Users.TESTER);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);

      Sector s1c = sm.get(s1);
      assertEquals("root-1", s1c.getSubject().getId());
      assertEquals("t4", s1c.getTarget().getId());

      Sector s2c = sm.get(s2);
      assertEquals("root-2", s2c.getSubject().getId());
      assertEquals("t5", s2c.getTarget().getId());
    }
  }

  static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, SimpleNameLink src, SimpleNameLink target) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(Datasets.COL);
      sector.setSubjectDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.applyUser(TestDataRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

}