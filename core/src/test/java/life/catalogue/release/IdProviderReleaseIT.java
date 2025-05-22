package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSyncTestBase;
import life.catalogue.common.io.Resources;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.NameUsageArchiver;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

@RunWith(Parameterized.class)
public class IdProviderReleaseIT {
  private static final Logger LOG = LoggerFactory.getLogger(IdProviderReleaseIT.class);

  final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule);

  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule testRules = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  final ReleaseConfig cfg = new ReleaseConfig();

  String project;
  final List<Release> releases = new ArrayList<>();
  int newDatasetKey;
  int attempt;

  static class Release {
    final int key;

    public Release(int key, int attempt, DatasetOrigin origin) {
      this.key = key;
      this.attempt = attempt;
      this.origin = origin;
    }

    final int attempt;
    final DatasetOrigin origin;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"cactus"}, {"agrioaphis"},
    });
  }

  public IdProviderReleaseIT(String project) {
    this.project = project.toLowerCase();
  }

  @Before
  public void init () throws Throwable {
    LOG.info("Prepare project {}", project);
    List<TxtTreeDataRule.TreeDataset> rules = new ArrayList<>();
    rules.add(new TxtTreeDataRule.TreeDataset(Datasets.COL, "idrelease-trees/" + project + "/project.txtree", "COL Checklist", DatasetOrigin.PROJECT));
    for (int i=1; i<100; i++) {
      DatasetOrigin origin = DatasetOrigin.RELEASE;
      String relResourceBase = "idrelease-trees/" + project + "/release"+i;
      String relResource = relResourceBase+".txtree";
      if (!Resources.exists(relResource)) {
        origin = DatasetOrigin.XRELEASE;
        relResource = relResourceBase+"x.txtree";
        if (!Resources.exists(relResource)) {
          attempt = i;
          newDatasetKey = 99+i;
          break;
        }
      }
      int key = 99+i;
      releases.add(new Release(key, i, origin));
      rules.add(new TxtTreeDataRule.TreeDataset(key, relResource, "COL Release "+i, origin));
    }
    LOG.info("Found {} releases for project {}", attempt-1, project);
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(rules)) {
      treeRule.before();
    }

    // update source_key, attempt and created timestamp in chronological order
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var stmt = session.getConnection().createStatement()
    ) {
      stmt.execute("UPDATE dataset SET source_key = 3, attempt=key-99, created=now() - interval '100 day' + interval '1 day' * (key-99)  WHERE key > 10");
    }
  }

  private void createDataset() {
    // create new release dataset & setup id mapping tables
    Dataset newRelease = new Dataset();
    newRelease.setKey(newDatasetKey);
    newRelease.setAttempt(attempt);
    newRelease.setTitle("NEW Release");
    newRelease.setOrigin(DatasetOrigin.RELEASE);
    newRelease.setType(DatasetType.TAXONOMIC);
    newRelease.setLicense(License.CC0);
    newRelease.applyUser(Users.TESTER);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      dm.createWithID(newRelease);

      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> {
        dmp.dropTable(t, Datasets.COL);
        dmp.createIdMapTable(t, Datasets.COL);
      });
    }
  }

  @Test
  public void mapIDs() throws Throwable {
    LOG.info("Match project {} using attempt {} and dataset key {}", project, attempt, newDatasetKey);
    // rebuild project archive
    var archiver = new NameUsageArchiver(SqlSessionFactoryRule.getSqlSessionFactory());
    archiver.rebuildProject(Datasets.COL, true);
    // rematch
    matchingRule.rematchAll();
    // create new release
    createDataset();
    // map ids
    LOG.info("Map IDs for project {}", project);
    IdProvider idp = new IdProvider(Datasets.COL, DatasetOrigin.RELEASE, attempt, newDatasetKey, cfg, SqlSessionFactoryRule.getSqlSessionFactory());
    final int nextKey = idp.previewNextKey();
    idp.mapIds();
    idp.report();
    LOG.info("{} new IDs have been issued", idp.previewNextKey()-nextKey);

    // copy usages to new dataset
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var nm = session.getMapper(NameMapper.class);
      var num = session.getMapper(NameUsageMapper.class);

      nm.copyDataset(Datasets.COL, newDatasetKey, true);
      num.copyDataset(Datasets.COL, newDatasetKey, true);
    }
    // verify result tree
    System.out.println("\n*** COMPARISON ***");
    // compare with expected tree
    SectorSyncTestBase.assertTree(project, newDatasetKey, null, Resources.stream("idrelease-trees/" + project + "/expected.txtree"), true);
  }

}