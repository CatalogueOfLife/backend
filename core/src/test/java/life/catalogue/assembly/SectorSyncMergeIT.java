package life.catalogue.assembly;

import life.catalogue.TestUtils;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.*;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.junit.*;
import life.catalogue.matching.decision.DecisionRematchRequest;
import life.catalogue.matching.decision.DecisionRematcher;
import life.catalogue.matching.decision.SectorRematchRequest;
import life.catalogue.matching.decision.SectorRematcher;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.release.XReleaseConfig;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
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
      {"exact-dupes", List.of("plazi")},
      {"metopiinae", List.of("taxref", "uksi")},
      {"machaeridia", List.of("pbdb")},
      {"africasia", List.of("systema-dipterorum", "plz20443", "plz37588")},
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
      {"saccolomataceae", List.of("orthiopteris")},
      {"protected", List.of("src")}, // XReleaseConfig.protectedGroups shields the Carabus subtree from merges
      {"bareauthorship", List.of("src")} // bare-name merge candidates must be filtered by authorship, see readme.md
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
      // project dataset settings
      var dsm = session.getMapper(DatasetMapper.class);
      var settings = new DatasetSettings();
      settings.enable(Setting.SECTOR_REMOVE_ORDINALS);
      dsm.updateSettings(Datasets.COL, settings, Users.TESTER);

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
        var tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, new ThumborService(new ThumborConfig()), NameUsageIndexService.passThru(), null, validator);
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

  private NameUsageBase getOneUsage(NameUsageMapper num, Name n) {
    var usages = num.listByNameID(n.getDatasetKey(), n.getId(), new Page());
    assertEquals(1, usages.size());
    return usages.get(0);
  }

  public void literatureValidate() {
    // check if reference were copied - cant be done with text tree easily
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var nm = session.getMapper(NameMapper.class);
      var rm = session.getMapper(ReferenceMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);

      for (Name n : nm.list(Datasets.COL, new Page(100))) {
        Reference ref;
        LOG.debug(n.getScientificName());
        switch (n.getScientificName()) {
          case "Aacanthocnema":
            // from bionames
            assertNotNull(n.getPublishedInId());
            ref = rm.get(DSID.of(Datasets.COL, n.getPublishedInId()));
            assertNotNull(ref);
            assertEquals("Tuthill, L. D., & Taylor, K. L. (1955). Australian genera of the Family Psyllidae (Hemiptera, Homoptera). Australian Journal of Zoology, 227–257. https://doi.org/10.1071/zo9550227", ref.getCitation());
            assertEquals("10.1071/zo9550227", ref.getCsl().getDOI());
            assertEquals("Australian genera of the Family Psyllidae (Hemiptera, Homoptera)", ref.getCsl().getTitle());
            break;

          case "Aacanthocnema burckhardti":
            // from bionames
            assertNotNull(n.getPublishedInId());
            ref = rm.get(DSID.of(Datasets.COL, n.getPublishedInId()));
            assertNotNull(ref);
            assertEquals("A new genus and ten new species of jumping plant lice (Hemiptera: Triozidae) from Allocasuarina (Casuarinaceae) in Australia. (2011). Zootaxa, 3009, 1–45. https://doi.org/10.11646/zootaxa.3009.1.1", ref.getCitation());
            assertEquals("10.11646/zootaxa.3009.1.1", ref.getCsl().getDOI());
            assertEquals("https://bionames.org/references/08f3a09b1eb3a4e3c8a83c26b7911de9", ref.getCsl().getURL());
            assertEquals("A new genus and ten new species of jumping plant lice (Hemiptera: Triozidae) from Allocasuarina (Casuarinaceae) in Australia", ref.getCsl().getTitle());
            var u = getOneUsage(num, n);
            var vs = vsm.getByName(u.getName());
            var v = vsm.addSources(vs);
            assertNotNull(v);
            assertEquals(1, v.getSecondarySources().size());
            break;

          case "Aacanthocnema dobsoni":
            // from afd
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
      DatasetDao ddao = new DatasetDao(null, null, null, null, TestUtils.mockedBroker());
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

  /**
   * Invoked by reflection from {@link #syncAndCompare()} for the "protected" project.
   * Asserts that the protected Carabus genus and its subtree received no merge changes,
   * while a sibling genus outside the protected group was merged normally.
   */
  public void protectedValidate() {
    // a new source species inside the protected genus must not be inserted
    assertNull("New source species must not be inserted into the protected Carabus subtree",
      getByName(Datasets.COL, Rank.SPECIES, "Carabus nemoralis"));

    // an existing member of the protected subtree must not receive updates (no authorship added)
    var auratus = getByName(Datasets.COL, Rank.SPECIES, "Carabus auratus");
    assertNotNull(auratus);
    assertNull("Authorship must not be merged into a taxon within a protected group", auratus.getName().getAuthorship());

    // a new species under a sibling genus outside the protected group must be merged normally
    assertNotNull("New species outside the protected group must be merged",
      getByName(Datasets.COL, Rank.SPECIES, "Bembidion properans"));
  }

  /**
   * Invoked by reflection from {@link #syncAndCompare()} for the "bareauthorship" project.
   * Note: the reflection lookup in syncAndCompare is {@code project + "Validate"} where
   * {@code project} has been lower-cased in the constructor, so this method name must stay an
   * all-lowercase prefix ("bareauthorship") plus "Validate" - no camelCase, no hyphen (project
   * names with a hyphen, like "author-dupes", cannot use this mechanism at all since it would
   * not be a legal Java identifier).
   * <p>
   * Asserts the strict bare-name merge gate: a bare name is only merged onto a target candidate
   * when it has authorship AND exactly one candidate sharing the matched canonical has identical
   * rank and {@code AuthorComparator.compare(incoming, candidate) == Equality.EQUAL}. See
   * txtree/bareauthorship/readme.md for the full scenario writeup.
   */
  public void bareauthorshipValidate() {
    // canonical "Aus bus" is ambiguous in the target (two authorship variants). The incoming bare,
    // unauthored "Aus bus" from the source has no authorship at all, so it is skipped outright and
    // both variants must remain untouched.
    var busVariants = listByName(Datasets.COL, Rank.SPECIES, "Aus bus");
    assertEquals(2, busVariants.size());
    var busAuthors = new HashSet<String>();
    for (var u : busVariants) {
      busAuthors.add(u.getName().getAuthorship());
    }
    assertEquals(Set.of("Mill.", "Linn."), busAuthors);

    // canonical "Aus cus" has an unauthored candidate plus an unrelated authored decoy "Aus cus Linn."
    // sharing the same canonical id. The incoming bare, authored "Aus cus Mill." cannot resolve against
    // either: compare(Mill., <unauthored>) is UNKNOWN (not EQUAL), compare(Mill., Linn.) is DIFFERENT.
    // Zero candidates survive the filter, so the merge is skipped and "Aus cus" stays unauthored.
    var cusVariants = listByName(Datasets.COL, Rank.SPECIES, "Aus cus");
    assertEquals(2, cusVariants.size());
    var cusAuthors = new HashSet<String>();
    for (var u : cusVariants) {
      cusAuthors.add(u.getName().getAuthorship());
    }
    var expectedCusAuthors = new HashSet<String>();
    expectedCusAuthors.add(null);
    expectedCusAuthors.add("Linn.");
    assertEquals(expectedCusAuthors, cusAuthors);

    // canonical "Aus dus" has a single candidate "Aus dus Mill." with no publishedInId. The incoming
    // bare, authored "Aus dus Mill." has identical rank and EQUAL authorship, so it is the one genuine
    // merge case (a): the merge fires and copies the incoming PUB reference onto the target name. The
    // enrichment isn't tree-visible (references aren't printed in the text tree), so assert it directly.
    var dus = getByName(Datasets.COL, Rank.SPECIES, "Aus dus");
    assertNotNull(dus);
    assertEquals("Mill.", dus.getName().getAuthorship());
    assertNotNull("Merge must have copied the incoming PUB reference", dus.getName().getPublishedInId());
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var rm = session.getMapper(ReferenceMapper.class);
      var ref = rm.get(DSID.of(Datasets.COL, dus.getName().getPublishedInId()));
      assertNotNull(ref);
      assertEquals("A revision of the genus Aus", ref.getCsl().getTitle());
    }

    // canonical "Aus eus" has a single candidate "Aus eus Linn.". The incoming bare, authored
    // "Aus eus Mill." has identical rank but DIFFERENT authorship (case c), so the one candidate is
    // filtered out, zero remain, and the merge is skipped - authorship must stay "Linn.". The incoming
    // name also carries a PUB reference (eusRef) that the target lacks; if the authorship-EQUAL clause
    // were ever loosened (e.g. to != DIFFERENT, letting UNKNOWN through) this candidate would still be
    // filtered out here since compare(Mill., Linn.) is strictly DIFFERENT - the PUB is there so that a
    // *different* regression, an accidental widening of the whole gate, would show up as an unwanted
    // publishedInId on the target.
    var eus = getByName(Datasets.COL, Rank.SPECIES, "Aus eus");
    assertNotNull(eus);
    assertEquals("Linn.", eus.getName().getAuthorship());
    assertNull("Authorship mismatch must not enrich the target with the incoming PUB reference",
      eus.getName().getPublishedInId());

    // canonical "Aus fus" has a single candidate "Aus fus Mill." at rank SPECIES. The incoming bare
    // name is also "Aus fus Mill." (identical genus/specificEpithet -> same canonical nidx bucket,
    // rank is not part of the canonical form) but tagged rank SPECIES_AGGREGATE ("Aus fus agg."),
    // and EQUAL authorship. Since the ranks differ, the `c.getRank() == n.getRank()` clause excludes
    // the candidate, zero remain, and the merge is skipped - the incoming PUB reference (fusRef) must
    // not be copied onto the target, proving the rank clause is load-bearing.
    var fus = getByName(Datasets.COL, Rank.SPECIES, "Aus fus");
    assertNotNull(fus);
    assertEquals("Mill.", fus.getName().getAuthorship());
    assertNull("Rank mismatch must not enrich the target with the incoming PUB reference",
      fus.getName().getPublishedInId());
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