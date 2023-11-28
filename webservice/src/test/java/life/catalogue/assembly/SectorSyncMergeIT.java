package life.catalogue.assembly;

import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.printer.TxtTreeDataRule;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
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
  String expectedFN;
  Set<Rank> ranks;
  List<String> trees;
  List<Sector> sectors = new ArrayList<>();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"issues", null, List.of("bad")},
      {"subgenera", null, List.of("carabcat", "2021")},
      {"subgenera", Rank.FAMILY, List.of("carabcat", "2021")},
      {"proparte", Rank.ORDER, List.of("3i")},
      {"homonyms", null, List.of("worms", "itis", "wcvp", "taxref", "ala", "ipni", "irmng")},
      {"convulatum", null, List.of("dyntaxa")},
      {"unranked", null, List.of("palaeo")},
      {"circular", null, List.of("src1", "src2", "src3")},
      {"biota2", null, List.of("ipni")},
      {"biota", null, List.of("wcvp", "lcvp", "ipni")}, // TODO: should be merged: Biota macrocarpa hort. ex Gordon AND Biota macrocarpa Godr.
      {"saccolomataceae", null, List.of("orthiopteris")}
    });
  }

  public SectorSyncMergeIT(String project, Rank maxRank, List<String> trees) {
    this.project = project.toLowerCase();
    this.ranks = maxRank == null ? null : Set.copyOf(RankUtils.maxRanks(maxRank));
    this.trees = trees;
    this.expectedFN = "expected";
    if (maxRank != null) {
      this.expectedFN = this.expectedFN + "-" + maxRank.name().toLowerCase();
    }
  }

  @Before
  public void init () throws Throwable {
    System.out.printf("\n\n*** Project %s ***\n\n", project);
    LOG.info("Project {}. {} trees: {}", project, ranks, trees);
    testNum++;
    // load text trees & create sectors
    List<TxtTreeDataRule.TreeDataset> data = new ArrayList<>();
    data.add(
      new TxtTreeDataRule.TreeDataset(Datasets.COL, "txtree/"+project + "/project.txtree", "COL Checklist", DatasetOrigin.PROJECT)
    );

    int dkey = 100;
    for (String tree : trees) {
      data.add(
        new TxtTreeDataRule.TreeDataset(dkey, "txtree/"+project + "/" + tree.toLowerCase()+".txtree", tree, DatasetOrigin.EXTERNAL)
      );
      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
      if (ranks != null) {
        s.setRanks(ranks);
      }
      s.setPriority(dkey-99);
      s.setNote(tree);
      sectors.add(s);
      dkey++;
    }

    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(data)) {
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
    assertTree(Datasets.COL, getClass().getResourceAsStream("/txtree/" + project + "/" + expectedFN + ".txtree"));
  }

}