package life.catalogue.assembly;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.*;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.DecisionMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;
import life.catalogue.junit.TxtTreeDataRule;
import life.catalogue.release.XReleaseConfig;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
  TreeMergeHandlerConfig mergeCfg;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"author-dupes", List.of("iucn", "beetles", "swiss", "taxref", "taiwan", "plazi1")},
      {"rankorder", List.of("itis", "wcvp", "wfo", "tpl")},
      {"vernacular", List.of("v1", "v2")}, // extended trees
      {"sector-parents", List.of("none", "subject", "target", "subject-target")},
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
    boolean rematchSectors = false; // do we need to rematch sectors?
    for (String tree : trees) {
      String resource = "txtree/"+project + "/" + tree.toLowerCase();
      data.add(
        new TxtTreeDataRule.TreeDataset(dkey, resource + ".txtree", tree, DatasetOrigin.EXTERNAL)
      );
      Sector s;
      // do we have a config file?
      try {
        s = YamlUtils.read(Sector.class, Resources.getResourceAsStream(resource + ".yaml"));
        if (s.getSubject() != null || s.getTarget() != null) {
          rematchSectors = true;
        }
      } catch (IOException e) {
        s = new Sector(); // use defaults
      }

      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
      s.setEntities(Set.of(EntityType.VERNACULAR, EntityType.TYPE_MATERIAL));
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

    // do we have a merge handler config file?
    try {
      var xcfg = YamlUtils.read(XReleaseConfig.class, Resources.getResourceAsStream("txtree/"+project + "/xcfg.yaml"));
      mergeCfg = new TreeMergeHandlerConfig(SqlSessionFactoryRule.getSqlSessionFactory(), xcfg, Datasets.COL, Users.TESTER);
    } catch (IOException e) {
    }

    // rematch all names
    matchingRule.rematchAll();
    if (rematchSectors) {
      // rematch sector subject/target
      final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      var nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
      var tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
      SectorDao dao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
      SectorRematchRequest req = new SectorRematchRequest();
      req.setTarget(true);
      req.setSubject(true);
      req.setDatasetKey(Datasets.COL);
      SectorRematcher.match(dao, req, Users.TESTER);
    }
  }

  @Test
  public void syncAndCompare() throws Throwable {
    Logger LOG = LoggerFactory.getLogger(getClass());
    LOG.info("HELLO sec");
    for (var s : sectors) {
      sync(s, mergeCfg);
    }
    assertTree(Datasets.COL, null, getClass().getResourceAsStream("/txtree/" + project + "/expected.txtree"));

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
      syncAll(null, mergeCfg);
      assertTree(Datasets.COL, getClass().getResourceAsStream("/txtree/" + project + "/expected-with-decisions.txtree"));
    }

    switch (project) {
      case "vernacular":
        validateVernacular(); break;
    }
  }

  private void validateVernacular() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var vnm = session.getMapper(VernacularNameMapper.class);

      var u = getByName(Datasets.COL, Rank.ORDER, "Diptera");
      assertVNames(u, 3, vnm);

      u = getByName(Datasets.COL, Rank.SPECIES, "Aedes albopictus");
      assertVNames(u, 2, vnm);

      u = getByName(Datasets.COL, Rank.SPECIES, "Drosophila melanogaster");
      assertVNames(u, 5, vnm);

      u = getByName(Datasets.COL, Rank.SPECIES, "Musca domestica");
      assertVNames(u, 2, vnm);

      u = getByName(Datasets.COL, Rank.PHYLUM, "Arthropoda");
      assertVNames(u, 2, vnm);
    }
  }

  void assertVNames(DSID<String> key, int num, VernacularNameMapper vnm) {
    var vnames = vnm.listByTaxon(key);
    assertEquals(num, vnames.size());
    vnames.forEach(v -> {
      if (v.getId() != 2) {
        assertNotNull(v.getSectorKey());
        assertNotNull(v.getLatin());
      }
      assertNotNull(v.getLanguage());
      assertNotNull(v.getName());
    });
  }

}