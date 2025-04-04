package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.junit.*;
import life.catalogue.matching.decision.DecisionRematchRequest;
import life.catalogue.matching.decision.DecisionRematcher;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.release.XReleaseConfig;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import org.apache.ibatis.io.Resources;
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

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.Assert.*;

/**
 * Parameterized SectorSync to test merge sectors with different sources.
 */
@RunWith(Parameterized.class)
public class SectorSyncMergeIT extends SectorSyncTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSyncMergeIT.class);
  private static final TypeReference<List<EditorialDecision>> decisionListTypeRef = new TypeReference<>() {};

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

  ProjectTestInfo info;


  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"literature", List.of("bionames", "afd-lit")},
      {"carcharhinus", List.of("worms", "itis", "taxref", "iucn", "dutch")},
      {"abas", List.of("worms", "pbdb")},
      {"doryphora", List.of("worms", "wcvp", "3i", "coleo", "pbdb", "zoobank")},
      {"bolyeriidae", List.of("itis", "reptiledb", "uksi", "pbdb")},
      {"myosotis", List.of("taxref", "uksi", "pbdb", "bavaria")},
      {"tetralobus", List.of("wfo", "bouchard", "plazi")},
      {"cactus", List.of("wfo", "wcvp", "grin", "taxref", "tpl", "pbdb")},
      {"aphanizomenon", List.of("worms","ncbi","dyntaxa")}, // https://github.com/CatalogueOfLife/xcol/issues/146
      {"falcata", List.of("griis")}, // https://github.com/CatalogueOfLife/xcol/issues/183
      {"anas", List.of("worms", "azores")},
      {"author-dupes", List.of("iucn", "beetles", "swiss", "taxref", "taiwan", "plazi1", "fake")},
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

  public static TxtTreeDataRule.TreeDataset getProjectDataRule(String project) {
    return new TxtTreeDataRule.TreeDataset(Datasets.COL, "txtree/" + project + "/project.txtree", "COL Checklist", DatasetOrigin.PROJECT);
  }

  public static class ProjectTestInfo {
    public final String project;
    public final List<String> sources;
    public final List<Sector> sectors = new ArrayList<>();
    public final List<EditorialDecision> decisions = new ArrayList<>();
    public final List<TxtTreeDataRule.TreeDataset> rules = new ArrayList<>();
    public boolean rematchSectors = false; // do we need to rematch sectors?
    public XReleaseConfig cfg;

    public ProjectTestInfo(String project, List<String> sources) {
      this.project = project;
      this.sources = sources;
    }

    public TreeMergeHandlerConfig buildMergeConfig() {
      return new TreeMergeHandlerConfig(PgSetupRule.getSqlSessionFactory(), cfg, Datasets.COL, Users.TESTER);
    }
  }

  public static ProjectTestInfo setupProject(String project, List<String> sources) throws Throwable {
    ProjectTestInfo info = new ProjectTestInfo(project, sources);
    // load text trees & create sectors
    info.rules.add(getProjectDataRule(project));
    int dkey = 100;
    for (String tree : sources) {
      String resource = "txtree/"+project + "/" + tree.toLowerCase();
      boolean nomenclatural = tree.equalsIgnoreCase("ipni") || tree.equalsIgnoreCase("zoobank");
      info.rules.add(
        new TxtTreeDataRule.TreeDataset(dkey, resource, DatasetOrigin.EXTERNAL, nomenclatural ? DatasetType.NOMENCLATURAL : DatasetType.TAXONOMIC)
      );
      Sector s;
      // do we have a sector file?
      try {
        s = YamlUtils.read(Sector.class, Resources.getResourceAsStream(resource + ".yaml"));
        if (s.getSubject() != null || s.getTarget() != null) {
          info.rematchSectors = true;
        }
      } catch (IOException e) {
        s = new Sector(); // use defaults
      }

      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
      if (s.getEntities() == null) {
        // default entities
        s.setEntities(Set.of(EntityType.NAME_USAGE, EntityType.VERNACULAR, EntityType.TYPE_MATERIAL));
      }
      s.setPriority(dkey-99);
      s.addNote(tree);
      info.sectors.add(s);

      // do we have a decisions file?
      try {
        var decisions = YamlUtils.read(decisionListTypeRef, Resources.getResourceAsStream(resource + "-decisions.yaml"));
        for (var d : decisions) {
          d.setDatasetKey(Datasets.COL);
          d.setSubjectDatasetKey(dkey);
          info.decisions.add(d);
        }
      } catch (IOException e) {
        // swallow
      }

      dkey++;
    }

    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(info.rules)) {
      treeRule.before();
    }

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      for (var s : info.sectors) {
        s.applyUser(Users.TESTER);
        sm.create(s);
        // bare name hack - if the keyword "bare name" is found in sector notes we will remove all usages!
        if (s.getNote() != null && s.getNote().contains("bare name")) {
          LOG.info("Convert all usages from source {} into bare names", s.getSubjectDatasetKey());
          var num = session.getMapper(NameUsageMapper.class);
          num.deleteByDataset(s.getSubjectDatasetKey());
        }
      }
      var dm = session.getMapper(DecisionMapper.class);
      for (var d : info.decisions) {
        d.applyUser(Users.TESTER);
        dm.create(d);
      }
    }

    // do we have a merge handler config file?
    try {
      info.cfg = YamlUtils.read(XReleaseConfig.class, Resources.getResourceAsStream("txtree/"+project + "/xcfg.yaml"));
    } catch (IOException e) {
      info.cfg = new XReleaseConfig();
    }

    //dumpNidx();
    if (info.rematchSectors || !info.decisions.isEmpty()) {
      // rematch sector subject/target
      final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      if (info.rematchSectors) {
        var nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
        var tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
        SectorDao dao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
        var req = new SectorRematchRequest();
        req.setTarget(true);
        req.setSubject(true);
        req.setDatasetKey(Datasets.COL);
        SectorRematcher.match(dao, req, Users.TESTER);
      }
      if (!info.decisions.isEmpty()) {
        var dao = new DecisionDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), validator);
        var req = new DecisionRematchRequest();
        req.setDatasetKey(Datasets.COL);
        DecisionRematcher.match(dao, req, Users.TESTER);
      }
    }
    return info;
  }

  @Before
  public void init () throws Throwable {
    System.out.printf("\n\n*** Project %s ***\n\n", project);
    LOG.info("Project {}. Trees: {}", project, trees);
    testNum++;
    // load text trees & create sectors
    this.info = setupProject(project, trees);
    // rematch all names
    matchingRule.rematchAll();
  }

  static void dumpNidx() {
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
    }
  }

  public void literatureValidate() {
    // check if reference were copied - cant be done with text tree easily
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var nm = session.getMapper(NameMapper.class);
      var rm = session.getMapper(ReferenceMapper.class);

      for (Name n : nm.list(Datasets.COL, new Page(100))) {
        Reference ref;
        LOG.debug(n.getScientificName());
        switch (n.getScientificName()) {
          case "Aacanthocnema":
            assertNotNull(n.getPublishedInId());
            ref = rm.get(DSID.of(Datasets.COL, n.getPublishedInId()));
            assertNotNull(ref);
            assertEquals("Tuthill, L. D., & Taylor, K. L. (1955). Australian genera of the Family Psyllidae (Hemiptera, Homoptera). Australian Journal of Zoology, 227–257. https://doi.org/10.1071/zo9550227", ref.getCitation());
            assertEquals("10.1071/zo9550227", ref.getCsl().getDOI());
            assertEquals("Australian genera of the Family Psyllidae (Hemiptera, Homoptera)", ref.getCsl().getTitle());
            break;

          case "Aacanthocnema burckhardti":
            assertNotNull(n.getPublishedInId());
            ref = rm.get(DSID.of(Datasets.COL, n.getPublishedInId()));
            assertNotNull(ref);
            assertEquals("A new genus and ten new species of jumping plant lice (Hemiptera: Triozidae) from Allocasuarina (Casuarinaceae) in Australia. (2011). Zootaxa, 3009, 1–45. https://doi.org/10.11646/zootaxa.3009.1.1", ref.getCitation());
            assertEquals("10.11646/zootaxa.3009.1.1", ref.getCsl().getDOI());
            assertEquals("https://bionames.org/references/08f3a09b1eb3a4e3c8a83c26b7911de9", ref.getCsl().getURL());
            assertEquals("A new genus and ten new species of jumping plant lice (Hemiptera: Triozidae) from Allocasuarina (Casuarinaceae) in Australia", ref.getCsl().getTitle());
            break;

          case "Aacanthocnema dobsoni":
            assertNotNull(n.getPublishedInId());
            ref = rm.get(DSID.of(Datasets.COL, n.getPublishedInId()));
            assertNotNull(ref);
            assertEquals("Froggatt, W. W. (1903). Australian Psyllidae. Part III. Proceedings of the Linnean Society of New South Wales, 315–337 (+ 2). https://biodiversity.org.au/afd/publication/c55e5de7-a0e1-4287-b023-547032310e04", ref.getCitation());
            assertNull(ref.getCsl().getDOI());
            assertEquals("https://biodiversity.org.au/afd/publication/c55e5de7-a0e1-4287-b023-547032310e04", ref.getCsl().getURL());
            assertEquals("Australian Psyllidae. Part III", ref.getCsl().getTitle());
            break;

          default:
            assertNull(n.getPublishedInId());
        }
        assertNull(n.getPublishedInPage());
        assertNull(n.getPublishedInPageLink());
      }
    }
  }

  @Test
  public void syncAndCompare() throws Throwable {
    var mcfg = info.buildMergeConfig();
    for (var s : info.sectors) {
      sync(s, mcfg);
    }
    assertTree(project, Datasets.COL, null, getClass().getResourceAsStream("/txtree/" + project + "/expected.txtree"));

    try {
      var method = SectorSyncMergeIT.class.getMethod(project+"Validate");
      LOG.info("Validate results with specific validation method");
      method.invoke(this);

    } catch (NoSuchMethodException e) {
      // fine
    }

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
      try (TxtTreeDataRule treeRule = new TxtTreeDataRule(List.of(getProjectDataRule(project)))) {
        treeRule.before();
      }
      matchingRule.rematch(Datasets.COL);

      // create decisions
      final var ref = new TypeReference<List<EditorialDecision>>() {};
      List<EditorialDecision> decisions = YamlUtils.read(ref, decRes);
      // update subject & source keys
      Map<String, Integer> sourceKeys = new HashMap<>();
      for (var s : info.sectors) {
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
      syncAll(null, mcfg);
      assertTree(project, Datasets.COL, getClass().getResourceAsStream("/txtree/" + project + "/expected-with-decisions.txtree"));
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