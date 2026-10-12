package life.catalogue.matching.decision;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DecisionDao;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.junit.MybatisTestUtils;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

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

  /**
   * A block made for a stray must not move onto a homonym placed elsewhere once the source changed its ids,
   * but it should follow its subject through rank levels inserted or removed by the source.
   * https://github.com/CatalogueOfLife/backend/issues/1639
   *
   * Diptera
   *   Muscidae
   *     Coenosiinae Verrall, 1888
   *       Limnophorini
   *         Limnophora Robineau-Desvoidy, 1830
   *         Spilogona
   *         Lispe Latreille, 1796
   *   Tachinidae
   */
  @Test
  public void parentAndAuthorship() {
    final int datasetKey = TestDataRule.APPLE.key;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      usage(session, datasetKey, "dip", null, "Diptera", null, Rank.ORDER);
      usage(session, datasetKey, "mus", "dip", "Muscidae", null, Rank.FAMILY);
      usage(session, datasetKey, "tac", "dip", "Tachinidae", null, Rank.FAMILY);
      usage(session, datasetKey, "coe", "mus", "Coenosiinae", Authorship.yearAuthors("1888", "Verrall"), Rank.SUBFAMILY);
      usage(session, datasetKey, "lmt", "coe", "Limnophorini", null, Rank.TRIBE);
      usage(session, datasetKey, "lim", "lmt", "Limnophora", Authorship.yearAuthors("1830", "Robineau-Desvoidy"), Rank.GENUS);
      usage(session, datasetKey, "spi", "lmt", "Spilogona", null, Rank.GENUS);
      usage(session, datasetKey, "lis", "lmt", "Lispe", Authorship.yearAuthors("1796", "Latreille"), Rank.GENUS);
    }

    // the stray from the previous import sat under Tachinidae
    var stray = createDecision(datasetKey, link("gone1", "Coenosiinae", null, Rank.SUBFAMILY, "Tachinidae"));
    // the real one
    var real = createDecision(datasetKey, link("gone2", "Coenosiinae", null, Rank.SUBFAMILY, "Muscidae"));
    // a tribe and a subfamily got inserted
    var inserted = createDecision(datasetKey, link("gone3", "Limnophora", null, Rank.GENUS, "Muscidae"));
    // the stored parent vanished from the source
    var removed = createDecision(datasetKey, link("gone4", "Spilogona", null, Rank.GENUS, "Phaoniinae"));
    // a different authorship
    var author = createDecision(datasetKey, link("gone5", "Coenosiinae", "Meigen, 1803", Rank.SUBFAMILY, null));
    // an id that still resolves is kept even though its parent moved
    var kept = createDecision(datasetKey, link("lmt", "Limnophorini", null, Rank.TRIBE, "Tachinidae"));
    // no parent at all, but an authorship formatted differently
    var noParent = createDecision(datasetKey, link("gone6", "Lispe", "Latreille 1796", Rank.GENUS, null));

    DecisionDao dao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
    DecisionRematchRequest req = new DecisionRematchRequest(Datasets.COL, false);
    req.setSubjectDatasetKey(datasetKey);
    DecisionRematcher.match(dao, req, Users.TESTER);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      assertNull(dm.get(stray).getSubject().getId());
      assertNull(dm.get(author).getSubject().getId());

      var d = dm.get(real);
      assertEquals("coe", d.getSubject().getId());
      // the subject is refreshed from the matched usage
      assertEquals("Muscidae", d.getSubject().getParent());
      assertEquals("Verrall, 1888", d.getSubject().getAuthorship());

      d = dm.get(inserted);
      assertEquals("lim", d.getSubject().getId());
      assertEquals("Limnophorini", d.getSubject().getParent());

      assertEquals("spi", dm.get(removed).getSubject().getId());
      assertEquals("lis", dm.get(noParent).getSubject().getId());

      d = dm.get(kept);
      assertEquals("lmt", d.getSubject().getId());
      // unchanged, so not refreshed
      assertEquals("Tachinidae", d.getSubject().getParent());
    }
  }

  static SimpleNameLink link(String id, String name, String authorship, Rank rank, String parent) {
    var sn = SimpleNameLink.of(id, name, authorship, rank);
    sn.setParent(parent);
    return sn;
  }

  static void usage(SqlSession session, int datasetKey, String id, String parentID, String name, Authorship authorship, Rank rank) {
    Name n = new Name();
    n.applyUser(TestDataRule.TEST_USER);
    n.setId(id);
    n.setDatasetKey(datasetKey);
    n.setUninomial(name);
    n.setRank(rank);
    if (authorship != null) {
      n.setCombinationAuthorship(authorship);
    }
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);
    n.rebuildScientificName();
    n.rebuildAuthorship();
    session.getMapper(NameMapper.class).create(n);
    Taxon t = TestEntityGenerator.newTaxon(n, id, parentID);
    t.setAccordingToId(null);
    session.getMapper(TaxonMapper.class).create(t);
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