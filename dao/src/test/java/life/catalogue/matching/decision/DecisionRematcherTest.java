package life.catalogue.matching.decision;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DecisionDao;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.es.NameUsageIndexService;

import org.gbif.nameparser.api.Rank;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DecisionRematcherTest {

  static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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

    DSID<Integer> d1 = createDecision(datasetKey,
      SimpleNameLink.of("xyz", "Larus fuscus", Rank.SPECIES)
    );
    DSID<Integer> d2 = createDecision(datasetKey,
      SimpleNameLink.of(null, "Larus fuscus", Rank.SPECIES)
    );
    DSID<Integer> d3 = createDecision(datasetKey,
      SimpleNameLink.of("null", "Larus", Rank.GENUS)
    );

    DecisionDao dao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    DecisionRematchRequest req = new DecisionRematchRequest(Datasets.COL, false);
    req.setSubjectDatasetKey(datasetKey);
    DecisionRematcher.match(dao, req, Users.TESTER);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);

      // we order decisions in reverse order when searching in pg, so last one gets matched not d1
      //     ORDER BY ed.id desc
      EditorialDecision d1b = dm.get(d1);
      assertNull(d1b.getSubject().getId());

      EditorialDecision d2b = dm.get(d2);
      assertEquals("root-2", d2b.getSubject().getId());

      EditorialDecision d3b = dm.get(d3);
      assertNull(d3b.getSubject().getId());
    }

    req.setSubjectDatasetKey(null);
    DecisionRematcher.match(dao, req, Users.TESTER);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);

      // we order decisions in reverse order when searching, so last one gets matched
      EditorialDecision d1b = dm.get(d1);
      assertNull(d1b.getSubject().getId());

      EditorialDecision d2b = dm.get(d2);
      assertEquals("root-2", d2b.getSubject().getId());

      EditorialDecision d3b = dm.get(d3);
      assertNull(d3b.getSubject().getId());
    }
  }

  static DSID<Integer> createDecision(int datasetKey, SimpleNameLink src) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      EditorialDecision d = new EditorialDecision();
      d.setMode(EditorialDecision.Mode.BLOCK);
      d.setDatasetKey(Datasets.COL);
      d.setSubjectDatasetKey(datasetKey);
      d.setSubject(src);
      d.applyUser(TestDataRule.TEST_USER);
      session.getMapper(DecisionMapper.class).create(d);
      return d;
    }
  }

}