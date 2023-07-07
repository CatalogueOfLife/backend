package life.catalogue.assembly;

import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.*;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.TxtTreeDataRule;

import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Testing SectorSync but also SectorDelete and SectorDeleteFull.
 * The test takes some time and prepares various sources for all tests, hence we test deletions here too avoiding duplication of the time consuming overhead.
 *
 * Before we start any test we prepare the db with imports that can be reused across tests later on.
 */
@RunWith(Parameterized.class)
public class SectorSyncMergeIT extends SectorSyncTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSyncMergeIT.class);

  final static SqlSessionFactoryRule pg = new PgSetupRule(); //PgConnectionRule("col", "postgres", "postgres");
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
      .outerRule(pg)
      .around(treeRepoRule);

  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();
  final SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @Rule
  public final TestRule testRules = RuleChain
    .outerRule(dataRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  int testNum = 0;
  String project;
  List<String> trees;
  List<Sector> sectors = new ArrayList<>();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"unranked", List.of("palaeo")},
      {"circular", List.of("src1", "src2", "src3")},
      {"biota", List.of("wcvp", "lcvp", "ipni")}, // TODO: should be merged: Biota macrocarpa hort. ex Gordon AND Biota macrocarpa Godr.
      {"saccolomataceae", List.of("orthiopteris")}
    });
  }

  public SectorSyncMergeIT(String project, List<String> trees) {
    this.project = project.toLowerCase();
    this.trees = trees;
  }

  @Before
  public void init () throws Throwable {
    LOG.info("Project {}. Trees: {}", project, trees);
    testNum++;
    // load text trees & create sectors
    Map<Integer, String> data = new HashMap<>();
    data.put(Datasets.COL, "txtree/"+project + "/project.txtree");

    int dkey = 100;
    for (String tree : trees) {
      data.put(dkey, "txtree/"+project + "/" + tree.toLowerCase()+".txtree");
      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
      s.setPriority(dkey-99);
      s.setNote(tree);
      sectors.add(s);
      dkey++;
    }

    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(data)) {
      treeRule.setOrigin(DatasetOrigin.EXTERNAL);
      treeRule.before();
    }

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      for (var s : sectors) {
        s.applyUser(Users.TESTER);
        sm.create(s);
      }
    }
    // rematch
    matchingRule.rematchAll();
  }

  @Test
  public void syncAndCompare() throws Exception {
    syncAll();
    assertTree(Datasets.COL, getClass().getResourceAsStream("/txtree/" + project + "/expected.txtree"));
  }

}