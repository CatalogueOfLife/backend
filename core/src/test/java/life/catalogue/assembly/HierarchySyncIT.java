package life.catalogue.assembly;

import life.catalogue.TestUtils;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.config.IdentifierScopeConfig;
import life.catalogue.config.MatchingConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.matching.IdentifierScopeResolver;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * End-to-end test for {@link HierarchySync}: builds a tiny target dataset and a project,
 * runs all three phases, and asserts on the outcome including idempotency on a second run.
 *
 * <p>Project (Datasets.COL = 3) initial state:
 * <pre>
 *   Felis (genus, root, ACCEPTED, identifier="test:T_Felis")
 *     └ Felis catus (species, ACCEPTED, identifier="test:T_Felis_catus")
 *   Lynx (genus, root, ACCEPTED in project — target says SYNONYM, identifier="test:T_Lynx")
 *   Canis (genus, root, ACCEPTED, identifier="test:T_Canis")
 * </pre>
 *
 * <p>Target external dataset:
 * <pre>
 *   Animalia (kingdom, root)
 *     ├ Felidae (family)
 *     │   └ Felis (genus)
 *     │       ├ Felis catus (species)
 *     │       │   └ Felis silvestris (synonym of Felis catus)
 *     │       └ Lynx (synonym of Felis)
 *     └ Canidae (family)
 *         └ Canis (genus)
 * </pre>
 *
 * <p>Expected after sync:
 * <ul>
 *   <li>Phase 1: Animalia, Felidae, Canidae imported into the project; project Felis and Canis
 *       rewired under Felidae and Canidae respectively (project Lynx is not rewired in phase 1
 *       because its target counterpart is a synonym).</li>
 *   <li>Phase 2: project Lynx demoted to SYNONYM with parent_id pointing at project Felis.</li>
 *   <li>Phase 3: Felis silvestris copied as a synonym under project Felis catus. Lynx is NOT
 *       copied as a synonym of project Felis (its target id is already represented in the project
 *       via the matched Lynx usage).</li>
 *   <li>Idempotency: a second run produces the same end-state.</li>
 * </ul>
 */
public class HierarchySyncIT {

  static final String SCOPE = "test";
  static final int PROJECT_KEY = Datasets.COL;
  static int targetKey;

  // target ids (deterministic so we can match them via identifier)
  static final String T_Animalia = "T_Animalia";
  static final String T_Felidae = "T_Felidae";
  static final String T_Canidae = "T_Canidae";
  static final String T_Felis = "T_Felis";
  static final String T_Felis_catus = "T_Felis_catus";
  static final String T_Felis_silvestris = "T_Felis_silvestris";
  static final String T_Lynx = "T_Lynx";
  static final String T_Canis = "T_Canis";

  // project ids
  static final String P_Felis = "p_Felis";
  static final String P_Felis_catus = "p_Felis_catus";
  static final String P_Lynx = "p_Lynx";
  static final String P_Canis = "p_Canis";

  static int USER; // initialised in @Before once TestDataRule has loaded

  static final SqlSessionFactoryRule pg = new PgSetupRule();
  static final TreeRepoRule treeRepoRule = new TreeRepoRule();
  static final NameMatchingRule matchingRule = new NameMatchingRule();

  @ClassRule
  public static final TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule)
    .around(matchingRule);

  // per-test: re-empties the schema so each @Test starts from scratch
  @org.junit.Rule
  public final TestDataRule dataRule = TestDataRule.empty();

  Sector hierarchySector;
  IdentifierScopeResolver scopeResolver;

  @BeforeClass
  public static void wipeCache() {
    DatasetInfoCache.CACHE.clear();
  }

  @AfterClass
  public static void clearCache() {
    DatasetInfoCache.CACHE.clear();
  }

  @Before
  public void setup() {
    USER = TestDataRule.TEST_USER.getKey();
    DatasetInfoCache.CACHE.clear();
    targetKey = createExternalDataset("hierarchy-sync-target");
    populateTargetDataset(targetKey);
    populateProjectDataset();
    hierarchySector = createHierarchySector(targetKey);

    IdentifierScopeConfig cfg = new IdentifierScopeConfig();
    cfg.mapping.put(SCOPE, targetKey);
    scopeResolver = new IdentifierScopeResolver(cfg);
  }

  @Test
  public void fullSyncAllPhases() throws Exception {
    runHierarchySync();

    // Phase 1: above-genus ancestors imported
    NameUsageBase animalia = getByName(PROJECT_KEY, Rank.KINGDOM, "Animalia");
    NameUsageBase felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae");
    NameUsageBase canidae = getByName(PROJECT_KEY, Rank.FAMILY, "Canidae");
    assertNotNull("Animalia should have been imported", animalia);
    assertNotNull("Felidae should have been imported", felidae);
    assertNotNull("Canidae should have been imported", canidae);
    // imported records carry the sector key (sector_mode is derived via JOIN with the sector table
    // when read by certain mappers — not stored on the row, so we don't assert it here)
    assertEquals(hierarchySector.getId(), animalia.getSectorKey());
    assertEquals(hierarchySector.getId(), felidae.getSectorKey());
    assertEquals(hierarchySector.getId(), canidae.getSectorKey());
    // and the target identifier was attached
    assertHasIdentifier(felidae, SCOPE, T_Felidae);
    assertHasIdentifier(animalia, SCOPE, T_Animalia);

    // Phase 1: project genera rewired under their imported families
    NameUsageBase pFelis = getByID(PROJECT_KEY, P_Felis);
    NameUsageBase pCanis = getByID(PROJECT_KEY, P_Canis);
    assertEquals("Felis should now be under Felidae", felidae.getId(), pFelis.getParentId());
    assertEquals("Canis should now be under Canidae", canidae.getId(), pCanis.getParentId());

    // Phase 2: Lynx demoted to synonym, parent pointing at Felis
    NameUsageBase pLynx = getByID(PROJECT_KEY, P_Lynx);
    assertTrue("Lynx should now be a synonym", pLynx.getStatus().isSynonym());
    assertEquals("Lynx parent should be project Felis", P_Felis, pLynx.getParentId());

    // Phase 3: Felis silvestris copied as synonym under project Felis catus
    List<NameUsageBase> silvestris = listByName(PROJECT_KEY, Rank.SPECIES, "Felis silvestris");
    assertEquals("expected exactly one Felis silvestris synonym", 1, silvestris.size());
    NameUsageBase syn = silvestris.get(0);
    assertTrue("Felis silvestris should be a synonym", syn.getStatus().isSynonym());
    assertEquals("Felis silvestris parent should be project Felis catus", P_Felis_catus, syn.getParentId());
    assertEquals("Felis silvestris should be tagged with the hierarchy sector", hierarchySector.getId(), syn.getSectorKey());
    assertHasIdentifier(syn, SCOPE, T_Felis_silvestris);

    // Phase 3: Lynx is NOT additionally copied as a synonym under project Felis — its target id is
    // already mapped to a project usage (the demoted p_Lynx)
    List<NameUsageBase> lynxes = listByName(PROJECT_KEY, Rank.GENUS, "Lynx");
    assertEquals("expected exactly one Lynx in the project", 1, lynxes.size());
    assertEquals(P_Lynx, lynxes.get(0).getId());
  }

  @Test
  public void idempotentRerun() throws Exception {
    runHierarchySync();
    // capture counts after first run
    int taxaAfterFirst = countDataset(PROJECT_KEY, false);
    int synAfterFirst = countDataset(PROJECT_KEY, true);

    runHierarchySync();
    int taxaAfterSecond = countDataset(PROJECT_KEY, false);
    int synAfterSecond = countDataset(PROJECT_KEY, true);

    assertEquals("a second sync must not duplicate accepted taxa", taxaAfterFirst, taxaAfterSecond);
    assertEquals("a second sync must not duplicate synonyms", synAfterFirst, synAfterSecond);

    // and the structural assertions still hold after the rerun
    NameUsageBase felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae");
    assertNotNull(felidae);
    NameUsageBase pFelis = getByID(PROJECT_KEY, P_Felis);
    assertEquals(felidae.getId(), pFelis.getParentId());
    NameUsageBase pLynx = getByID(PROJECT_KEY, P_Lynx);
    assertTrue(pLynx.getStatus().isSynonym());
  }

  // ---------- helpers ----------

  private void runHierarchySync() throws Exception {
    SectorDao sdao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), null, null);
    SectorImportDao siDao = new SectorImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), TreeRepoRule.getRepo());
    EventBroker bus = TestUtils.mockedBroker();
    NameIndex ni = NameMatchingRule.getIndex();
    try (UsageMatcherFactory matcherFactory = new UsageMatcherFactory(new MatchingConfig(), ni, SqlSessionFactoryRule.getSqlSessionFactory(), null)) {
      HierarchySync sync = new HierarchySync(
        hierarchySector,
        SqlSessionFactoryRule.getSqlSessionFactory(),
        ni,
        session -> matcherFactory.postgres(PROJECT_KEY, session),
        LatestDatasetKeyCache.passThru(),
        bus,
        NameUsageIndexService.passThru(),
        sdao,
        siDao,
        r -> {},
        (r, e) -> { throw new AssertionError("HierarchySync failed", e); },
        scopeResolver,
        USER
      );
      sync.run();
      if (sync.getState().getState() != ImportState.FINISHED) {
        throw new AssertionError("HierarchySync did not finish cleanly: state=" + sync.getState().getState() + " error=" + sync.getState().getError());
      }
    }
  }

  private static int createExternalDataset(String title) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Dataset d = new Dataset();
      d.setTitle(title);
      d.setOrigin(DatasetOrigin.EXTERNAL);
      d.setType(DatasetType.TAXONOMIC);
      d.setLicense(License.CC0);
      d.setPrivat(false);
      d.setGbifKey(UUID.randomUUID());
      d.applyUser(Users.DB_INIT);
      s.getMapper(DatasetMapper.class).create(d);
      return d.getKey();
    }
  }

  private static void populateTargetDataset(int dsKey) {
    insertTaxon(dsKey, T_Animalia, null, Rank.KINGDOM, "Animalia");
    insertTaxon(dsKey, T_Felidae, T_Animalia, Rank.FAMILY, "Felidae");
    insertTaxon(dsKey, T_Canidae, T_Animalia, Rank.FAMILY, "Canidae");
    insertTaxon(dsKey, T_Felis, T_Felidae, Rank.GENUS, "Felis");
    insertTaxon(dsKey, T_Canis, T_Canidae, Rank.GENUS, "Canis");
    insertTaxon(dsKey, T_Felis_catus, T_Felis, Rank.SPECIES, "Felis catus");
    // synonyms have status=SYNONYM and parent_id pointing at the accepted taxon
    insertSynonym(dsKey, T_Lynx, T_Felis, Rank.GENUS, "Lynx");
    insertSynonym(dsKey, T_Felis_silvestris, T_Felis_catus, Rank.SPECIES, "Felis silvestris");
  }

  private static void populateProjectDataset() {
    insertTaxonWithIdentifier(PROJECT_KEY, P_Felis, null, Rank.GENUS, "Felis", T_Felis);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Felis_catus, P_Felis, Rank.SPECIES, "Felis catus", T_Felis_catus);
    // Lynx is accepted in the project even though target says synonym - phase 2 will demote it
    insertTaxonWithIdentifier(PROJECT_KEY, P_Lynx, null, Rank.GENUS, "Lynx", T_Lynx);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Canis, null, Rank.GENUS, "Canis", T_Canis);
  }

  private static void insertTaxon(int datasetKey, String id, String parentId, Rank rank, String scientificName) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = buildName(datasetKey, id, scientificName, rank);
      s.getMapper(NameMapper.class).create(n);
      Taxon t = buildTaxon(datasetKey, id, parentId, n, TaxonomicStatus.ACCEPTED);
      s.getMapper(TaxonMapper.class).create(t);
    }
  }

  private static void insertSynonym(int datasetKey, String id, String acceptedId, Rank rank, String scientificName) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = buildName(datasetKey, id, scientificName, rank);
      s.getMapper(NameMapper.class).create(n);
      Synonym syn = new Synonym();
      syn.setDatasetKey(datasetKey);
      syn.setId(id);
      syn.setName(n);
      syn.setStatus(TaxonomicStatus.SYNONYM);
      syn.setParentId(acceptedId);
      syn.setOrigin(life.catalogue.api.vocab.Origin.SOURCE);
      syn.applyUser(USER);
      s.getMapper(SynonymMapper.class).create(syn);
    }
  }

  private static void insertTaxonWithIdentifier(int datasetKey, String id, String parentId, Rank rank, String scientificName, String targetId) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = buildName(datasetKey, id, scientificName, rank);
      s.getMapper(NameMapper.class).create(n);
      Taxon t = buildTaxon(datasetKey, id, parentId, n, TaxonomicStatus.ACCEPTED);
      t.setIdentifier(new java.util.ArrayList<>(List.of(new Identifier(SCOPE, targetId))));
      s.getMapper(TaxonMapper.class).create(t);
    }
  }

  private static Name buildName(int datasetKey, String id, String scientificName, Rank rank) {
    Name n = new Name();
    n.setDatasetKey(datasetKey);
    n.setId(id + "_n");
    n.setScientificName(scientificName);
    n.setRank(rank);
    n.setType(NameType.SCIENTIFIC);
    n.setOrigin(life.catalogue.api.vocab.Origin.SOURCE);
    n.setCode(NomCode.ZOOLOGICAL);
    if (rank.isGenusOrSuprageneric()) {
      n.setUninomial(scientificName);
    } else {
      String[] parts = scientificName.split(" ", 2);
      n.setGenus(parts[0]);
      if (parts.length > 1) {
        n.setSpecificEpithet(parts[1]);
      }
    }
    n.applyUser(USER);
    return n;
  }

  private static Taxon buildTaxon(int datasetKey, String id, String parentId, Name name, TaxonomicStatus status) {
    Taxon t = new Taxon();
    t.setDatasetKey(datasetKey);
    t.setId(id);
    t.setName(name);
    t.setStatus(status);
    t.setParentId(parentId);
    t.setOrigin(life.catalogue.api.vocab.Origin.SOURCE);
    t.applyUser(USER);
    return t;
  }

  private static Sector createHierarchySector(int targetDatasetKey) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(Sector.Mode.HIERARCHY);
      sector.setDatasetKey(PROJECT_KEY);
      sector.setSubjectDatasetKey(targetDatasetKey);
      sector.setUseXRelease(true);
      sector.applyUser(USER);
      s.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

  // ---------- assertion helpers ----------

  private static NameUsageBase getByID(int datasetKey, String id) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return s.getMapper(NameUsageMapper.class).get(DSID.of(datasetKey, id));
    }
  }

  private static NameUsageBase getByName(int datasetKey, Rank rank, String name) {
    List<NameUsageBase> results = listByName(datasetKey, rank, name);
    if (results.isEmpty()) return null;
    if (results.size() > 1) throw new IllegalStateException("Multiple usages found for " + name + " (rank=" + rank + ")");
    return results.get(0);
  }

  private static List<NameUsageBase> listByName(int datasetKey, Rank rank, String name) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return s.getMapper(NameUsageMapper.class).listByName(datasetKey, name, rank, new Page(0, 100));
    }
  }

  private static int countDataset(int datasetKey, boolean synonyms) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      if (synonyms) {
        return s.getMapper(SynonymMapper.class).count(datasetKey);
      }
      return s.getMapper(TaxonMapper.class).count(datasetKey);
    }
  }

  private static void assertHasIdentifier(NameUsageBase u, String scope, String value) {
    assertNotNull("usage should not be null", u);
    List<Identifier> ids = u.getIdentifier();
    assertNotNull("usage should have identifiers", ids);
    boolean found = ids.stream().anyMatch(id -> scope.equalsIgnoreCase(id.getScope()) && value.equals(id.getId()));
    assertTrue("usage " + u.getId() + " should carry identifier " + scope + ":" + value + " but had " + ids, found);
  }
}
