package life.catalogue.assembly;

import com.fasterxml.jackson.core.type.TypeReference;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.matching.NameIndexImpl;
import life.catalogue.printer.TxtTreeDataRule;

import org.apache.ibatis.io.Resources;

import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.RankUtils;

import java.io.IOException;
import java.io.InputStream;
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
 * Parameterized SectorSync to test merge sectors with different sources.
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
      {"sic", List.of("millibase")},
      {"protoperidinium", List.of("itis", "worms", "brazil", "taxref", "dyntaxa", "artsnavebasen", "griis")},
      {"clavaria", List.of("unite")},
      {"issues", List.of("bad")},
      {"subgenera", List.of("carabcat", "2021")},
      {"subgenera-fam", List.of("carabcat", "2021")},
      {"proparte", List.of("3i")},
      {"homonyms", List.of("worms", "itis", "wcvp", "taxref", "ala", "ipni", "irmng")},
      {"convulatum", List.of("dyntaxa")},
      {"unranked", List.of("palaeo")},
      {"circular", List.of("src1", "src2", "src3")},
      {"biota2", List.of("ipni")},
      {"biota", List.of("wcvp", "lcvp", "ipni")}, // TODO: should be merged: Biota macrocarpa hort. ex Gordon AND Biota macrocarpa Godr.
      {"saccolomataceae", List.of("orthiopteris")}
    });
  }

  public SectorSyncMergeIT(String project, List<String> trees) {
    this.project = project.toLowerCase();
    this.trees = trees;
  }

  private TxtTreeDataRule.TreeDataset getProjectDataRule() {
    return new TxtTreeDataRule.TreeDataset(Datasets.COL, "txtree/"+project + "/project.txtree", "COL Checklist", DatasetOrigin.PROJECT);
  }

  @Before
  public void init () throws Throwable {
    System.out.printf("\n\n*** Project %s ***\n\n", project);
    LOG.info("Project {}. Trees: {}", project, trees);
    testNum++;
    // load text trees & create sectors
    List<TxtTreeDataRule.TreeDataset> data = new ArrayList<>();
    data.add(getProjectDataRule());

    int dkey = 100;
    for (String tree : trees) {
      String resource = "txtree/"+project + "/" + tree.toLowerCase();
      data.add(
        new TxtTreeDataRule.TreeDataset(dkey, resource + ".txtree", tree, DatasetOrigin.EXTERNAL)
      );
      Sector s;
      // do we have a config file?
      try {
        s = YamlUtils.read(Sector.class, Resources.getResourceAsStream(resource + ".yaml"));
      } catch (IOException e) {
        s = new Sector(); // use defaults
      }

      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
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
  public void syncAndCompare() throws Throwable {
    syncAll();
    assertTree(Datasets.COL, getClass().getResourceAsStream("/txtree/" + project + "/expected.txtree"));

    // do once more with decisions?
    var decRes = getClass().getResourceAsStream("/txtree/" + project + "/decisions.yaml");
    if (decRes != null) {
      LOG.info("Test project {} again with decisions!", project);
      // reset project
      DatasetDao ddao = new DatasetDao(null, null, null, null);
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(false)) {
        ddao.deleteData(Datasets.COL, session);
        session.commit();
      }
      try (TxtTreeDataRule treeRule = new TxtTreeDataRule(List.of(getProjectDataRule()))) {
        treeRule.before();
      }
      matchingRule.rematch(Datasets.COL);

      // create decisions
      final var ref = new TypeReference<List<EditorialDecision>>() {};
      List<EditorialDecision> decisions = YamlUtils.read(ref, decRes);
      // update subject & source keys
      Map<String, Integer> sourceKeys = new HashMap<>();
      for (var s : sectors) {
        if (s.getNote() != null) {
          sourceKeys.put(s.getNote().trim().toLowerCase(), s.getSubjectDatasetKey());
        }
      }
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        DecisionMapper dm = session.getMapper(DecisionMapper.class);
        for (var d : decisions) {
          String tree = d.getNote().trim().toLowerCase();
          if (!sourceKeys.containsKey(tree)) {
            throw new IllegalStateException("Could not find decision subject dataset " + d.getNote());
          }
          d.setDatasetKey(Datasets.COL);
          d.setSubjectDatasetKey(sourceKeys.get(tree));
          d.setOriginalSubjectId(d.getSubject().getId());
          d.applyUser(Users.TESTER);
          dm.create(d);
        }
      }

      // sync and verify
      syncAll();
      assertTree(Datasets.COL, getClass().getResourceAsStream("/txtree/" + project + "/expected-with-decisions.txtree"));
    }
  }

}