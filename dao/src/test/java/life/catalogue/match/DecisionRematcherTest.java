package life.catalogue.match;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.MybatisTestUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    int d1 = createDecision(datasetKey,
      new SimpleName("xyz", "Larus fuscus", Rank.SPECIES)
    );
    int d2 = createDecision(datasetKey,
      new SimpleName(null, "Larus fuscus", Rank.SPECIES)
    );
    int d3 = createDecision(datasetKey,
      new SimpleName("null", "Larus", Rank.GENUS)
    );

    DecisionDao dao = new DecisionDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru());
    DecisionRematchRequest req = new DecisionRematchRequest(Datasets.DRAFT_COL, false);
    req.setSubjectDatasetKey(datasetKey);
    DecisionRematcher.match(dao, req, Users.TESTER);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);

      // we order decisions in reverse order when searching, so last one gets matched not d1
      EditorialDecision d1b = dm.get(d1);
      assertNull(d1b.getSubject().getId());

      EditorialDecision d2b = dm.get(d2);
      assertEquals("root-2", d2b.getSubject().getId());

      EditorialDecision d3b = dm.get(d3);
      assertNull(d3b.getSubject().getId());
    }

    req.setSubjectDatasetKey(null);
    DecisionRematcher.match(dao, req, Users.TESTER);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
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

  static int createDecision(int datasetKey, SimpleName src) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      EditorialDecision d = new EditorialDecision();
      d.setMode(EditorialDecision.Mode.BLOCK);
      d.setDatasetKey(Datasets.DRAFT_COL);
      d.setSubjectDatasetKey(datasetKey);
      d.setSubject(src);
      d.applyUser(TestDataRule.TEST_USER);
      session.getMapper(DecisionMapper.class).create(d);
      return d.getId();
    }
  }

}