package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TypeMaterialMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
import life.catalogue.junit.*;
import life.catalogue.matching.nidx.NameIndexImpl;
import life.catalogue.release.XReleaseConfig;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class SectorSyncNameUpdIT extends SectorSyncTestBase {

  final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule(true);
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  public final TestDataRule dataRule = TestDataRule.draftNameUpd();

  @Rule
  public final TestRule testRules = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  TaxonDao tdao;

  @Before
  public void init () throws IOException, SQLException {
    tdao = syncFactoryRule.getTdao();
  }

  @After
  public void after () throws IOException, SQLException {
  }

  Sector sec(int dkey, EntityType... entities){
    Sector s = new Sector();
    s.setDatasetKey(Datasets.COL);
    s.applyUser(Users.TESTER);
    s.setMode(Sector.Mode.MERGE);
    s.setSubjectDatasetKey(dkey);
    s.setEntities(Set.of(entities));
    return s;
  }

  private void dumpNidx() {
    NameMatchingRule.getIndex().printIndex();
  }

  @Test
  public void testNameUpdates() {
    XReleaseConfig cfg = new XReleaseConfig();
    TreeMergeHandlerConfig mergeCfg = new TreeMergeHandlerConfig(PgSetupRule.getSqlSessionFactory(), cfg, Datasets.COL, Users.TESTER);

    var sectors = new ArrayList<Sector>();
    var s100 = sec(100, EntityType.NAME, EntityType.REFERENCE);
    sectors.add(s100);
    var s101 = sec(101, EntityType.NAME, EntityType.REFERENCE);
    sectors.add(s101);

    final var key = DSID.of(Datasets.COL, "1"); // Aaata brepha - name & usage id is the same!

    dumpNidx();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      var vm = session.getMapper(VernacularNameMapper.class);
      var tmm = session.getMapper(TypeMaterialMapper.class);
      var nrm = session.getMapper(NameRelationMapper.class);

      int prio = 100;
      for (var s : sectors) {
        s.setPriority(prio--);
        sm.create(s);
        session.commit();
        sync(s, mergeCfg);
      }

      Assert.assertTrue(vm.listByTaxon(key).isEmpty());
      Assert.assertTrue(tmm.listByName(key).isEmpty());
    }

    // sync again, this time with vernacular & distribution
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      s101.setEntities(Set.of(EntityType.VERNACULAR, EntityType.TYPE_MATERIAL));
      sm.update(s101);
      session.commit();

      sync(s101, mergeCfg);

      var vm = session.getMapper(VernacularNameMapper.class);
      var tmm = session.getMapper(TypeMaterialMapper.class);

      dumpNidx();
      Assert.assertFalse(vm.listByTaxon(key).isEmpty());
      Assert.assertFalse(tmm.listByName(key).isEmpty());
    }
  }

}