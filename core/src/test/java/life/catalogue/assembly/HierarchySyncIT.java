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
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

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

    // Phase 1: matched accepted species keeps its existing matched genus parent (Felis catus must
    // stay under project Felis, not be re-anchored under the imported Felidae).
    NameUsageBase pFelisCatus = getByID(PROJECT_KEY, P_Felis_catus);
    assertEquals("Felis catus should stay under project Felis, not jump to Felidae", P_Felis, pFelisCatus.getParentId());

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

    // VerbatimSource: every synced record (imported ancestor + copied synonym) must carry a
    // verbatim_source row linking it back to its origin in the source dataset.
    assertVerbatimSource(felidae, targetKey, T_Felidae);
    assertVerbatimSource(animalia, targetKey, T_Animalia);
    assertVerbatimSource(canidae, targetKey, T_Canidae);
    assertVerbatimSource(syn, targetKey, T_Felis_silvestris);

    // Phase 3: Lynx is NOT additionally copied as a synonym under project Felis — its target id is
    // already mapped to a project usage (the demoted p_Lynx)
    List<NameUsageBase> lynxes = listByName(PROJECT_KEY, Rank.GENUS, "Lynx");
    assertEquals("expected exactly one Lynx in the project", 1, lynxes.size());
    assertEquals(P_Lynx, lynxes.get(0).getId());
  }

  /**
   * Regression for the Sabulina parent-cycle bug. Project sec X has an inverted synonymy compared
   * to the target: A is accepted with B as its synonym in the project, but the target has B
   * accepted with A as its synonym. Phase 2 must not produce the 2-cycle A ↔ B (which the old
   * code did, because it happily resolved a target accepted id to the project synonym that
   * carried that id and used the synonym as a parent_id). Since promotions run before demotions
   * the pair ends up exactly as in the target.
   */
  @Test
  public void invertedSynonymyDoesNotCreateCycle() throws Exception {
    final String P_A = "p_invertedA";
    final String P_B = "p_invertedB";
    final String T_A = "T_invertedA"; // synonym in target
    final String T_B = "T_invertedB"; // accepted in target
    final String T_TestGenus = "T_TestGenus";

    // Target: TestGenus (root genus) > B (accepted species) > A (synonym of B)
    insertTaxon(targetKey, T_TestGenus, null, Rank.GENUS, "Testgenus");
    insertTaxon(targetKey, T_B, T_TestGenus, Rank.SPECIES, "Testgenus betaspec");
    insertSynonym(targetKey, T_A, T_B, Rank.SPECIES, "Testgenus alphaspec");

    // Project: A accepted (root, identifier=T_A), B synonym of A (identifier=T_B) — INVERTED vs target.
    insertTaxonWithIdentifier(PROJECT_KEY, P_A, null, Rank.SPECIES, "Testgenus alphaspec", T_A);
    insertSynonymWithIdentifier(PROJECT_KEY, P_B, P_A, Rank.SPECIES, "Testgenus betaspec", T_B);

    runHierarchySync();

    NameUsageBase a = getByID(PROJECT_KEY, P_A);
    NameUsageBase b = getByID(PROJECT_KEY, P_B);
    assertNotNull(a);
    assertNotNull(b);

    // Promotions run before demotions: B leaves A first, so A can then become B's synonym without closing a loop.
    assertTrue("B is accepted in the target and must have been promoted", b.getStatus().isTaxon());
    assertNotEquals("P_B must no longer point at P_A", P_A, b.getParentId());
    assertTrue("A is a synonym in the target and must have been demoted", a.getStatus().isSynonym());
    assertEquals("A must have become a synonym of B", P_B, a.getParentId());
  }

  /**
   * Regression for the 2026-09-02 OOM. The source has a parent cycle between two accepted species,
   * so phase 4 walks a chain for each that resolves to the other. Rewiring both would close the very
   * A ↔ B loop that made the following ES reindex build a 95 million entry classification and take the
   * rw server down. The second rewire must be blocked.
   */
  @Test
  public void mutualRewireDoesNotCreateCycle() throws Exception {
    final String P_A = "p_mutualA";
    final String P_B = "p_mutualB";
    final String T_A = "T_mutualA";
    final String T_B = "T_mutualB";

    // Target: A and B are each others parent - a cycle in the source data. The (dataset_key, parent_id)
    // FK means a cycle can only ever be closed by an update, never by an insert - which is exactly how
    // rewireProjectParents produced one in production.
    insertTaxon(targetKey, T_A, null, Rank.SPECIES, "Prunus domestica insititia");
    insertTaxon(targetKey, T_B, null, Rank.SUBSPECIES, "Prunus domestica subsp. insititia");
    setParent(targetKey, T_A, T_B);
    setParent(targetKey, T_B, T_A);

    // Project: both matched by identifier, both roots
    insertTaxonWithIdentifier(PROJECT_KEY, P_A, null, Rank.SPECIES, "Prunus domestica insititia", T_A);
    insertTaxonWithIdentifier(PROJECT_KEY, P_B, null, Rank.SUBSPECIES, "Prunus domestica subsp. insititia", T_B);

    runHierarchySync();

    NameUsageBase a = getByID(PROJECT_KEY, P_A);
    NameUsageBase b = getByID(PROJECT_KEY, P_B);
    assertNotNull(a);
    assertNotNull(b);
    assertFalse("P_A and P_B must not end up as each others parent",
      P_B.equals(a.getParentId()) && P_A.equals(b.getParentId()));
  }

  /**
   * Regression for the Vicia/Lentilla reassignment scenario. Target reassigns Vicia and its
   * species under a different accepted genus (Lentilla) by treating the project's accepted names
   * as synonyms of accepted Lentilla counterparts. The hierarchy sync must demote both accepted
   * project records into synonyms of the target-accepted equivalents, rather than rewiring them
   * under a higher classification rank.
   */
  @Test
  public void viciaLentillaReassignmentDemotesAcceptedToSynonym() throws Exception {
    final String T_Fabaceae = "T_Fabaceae";
    final String T_Lentilla = "T_Lentilla";
    final String T_Lentilla_faba = "T_Lentilla_faba";
    final String T_Vicia = "T_Vicia";
    final String T_Vicia_faba = "T_Vicia_faba";

    // Target: Animalia (already in fixture) > Fabaceae (family) > Lentilla (acc) > Lentilla faba (acc);
    // Vicia is a synonym of Lentilla, Vicia faba is a synonym of Lentilla faba.
    insertTaxon(targetKey, T_Fabaceae, T_Animalia, Rank.FAMILY, "Fabaceae");
    insertTaxon(targetKey, T_Lentilla, T_Fabaceae, Rank.GENUS, "Lentilla");
    insertTaxon(targetKey, T_Lentilla_faba, T_Lentilla, Rank.SPECIES, "Lentilla faba");
    insertSynonym(targetKey, T_Vicia, T_Lentilla, Rank.GENUS, "Vicia");
    insertSynonym(targetKey, T_Vicia_faba, T_Lentilla_faba, Rank.SPECIES, "Vicia faba");

    // Project: Lentilla / Lentilla faba and Vicia / Vicia faba all accepted; identifiers point at
    // the corresponding target ids (accepted ids for Lentilla pair, synonym ids for Vicia pair).
    final String P_Lentilla = "p_Lentilla";
    final String P_Lentilla_faba = "p_Lentilla_faba";
    final String P_Vicia = "p_Vicia";
    final String P_Vicia_faba = "p_Vicia_faba";
    insertTaxonWithIdentifier(PROJECT_KEY, P_Lentilla, null, Rank.GENUS, "Lentilla", T_Lentilla);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Lentilla_faba, P_Lentilla, Rank.SPECIES, "Lentilla faba", T_Lentilla_faba);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Vicia, null, Rank.GENUS, "Vicia", T_Vicia);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Vicia_faba, P_Vicia, Rank.SPECIES, "Vicia faba", T_Vicia_faba);

    runHierarchySync();

    // Lentilla and Lentilla faba stay accepted.
    NameUsageBase pLentilla = getByID(PROJECT_KEY, P_Lentilla);
    NameUsageBase pLentillaFaba = getByID(PROJECT_KEY, P_Lentilla_faba);
    assertNotNull(pLentilla);
    assertNotNull(pLentillaFaba);
    assertTrue("Lentilla should stay accepted", pLentilla.getStatus().isTaxon());
    assertTrue("Lentilla faba should stay accepted", pLentillaFaba.getStatus().isTaxon());
    // Lentilla faba sits under its matched genus Lentilla (species-under-matched-genus, not jumping to family).
    assertEquals("Lentilla faba should stay under project Lentilla", P_Lentilla, pLentillaFaba.getParentId());

    // Vicia: demoted to synonym, parent = project Lentilla.
    NameUsageBase pVicia = getByID(PROJECT_KEY, P_Vicia);
    assertNotNull(pVicia);
    assertTrue("Vicia should now be a synonym", pVicia.getStatus().isSynonym());
    assertEquals("Vicia synonym should point at project Lentilla", P_Lentilla, pVicia.getParentId());

    // Vicia faba: demoted to synonym, parent = project Lentilla faba.
    NameUsageBase pViciaFaba = getByID(PROJECT_KEY, P_Vicia_faba);
    assertNotNull(pViciaFaba);
    assertTrue("Vicia faba should now be a synonym", pViciaFaba.getStatus().isSynonym());
    assertEquals("Vicia faba synonym should point at project Lentilla faba", P_Lentilla_faba, pViciaFaba.getParentId());
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

  /**
   * Phase 4 with {@link Sector.AuthorshipUpdate#MISSING}: a matched project name that lacks an
   * authorship gets it copied from the source, and the source is tracked as an
   * {@link InfoGroup#AUTHORSHIP} secondary source on the project name's verbatim source.
   */
  @Test
  public void enrichesMissingAuthorshipFromSource() throws Exception {
    final String T_Auth = "T_Authoria";
    final String P_Auth = "p_Authoria";
    insertTaxonWithAuthorship(targetKey, T_Auth, T_Animalia, Rank.GENUS, "Authoria", auth("Smith", "1850"), null);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Auth, null, Rank.GENUS, "Authoria", T_Auth);

    setAuthorshipUpdate(Sector.AuthorshipUpdate.MISSING);
    runHierarchySync();

    Name pn = getName(PROJECT_KEY, P_Auth);
    assertNotNull(pn);
    assertTrue("project Authoria should have gained an authorship", pn.hasAuthorship());
    assertTrue("authorship should come from the source", pn.getAuthorship().contains("Smith") && pn.getAuthorship().contains("1850"));
    assertAuthorshipSecondarySource(PROJECT_KEY, P_Auth, targetKey, T_Auth);
  }

  /**
   * Phase 4 with {@link Sector.AuthorshipUpdate#MISSING}: a matched project name that already has
   * an authorship is left untouched even though the source has a different one.
   */
  @Test
  public void missingModeKeepsExistingAuthorship() throws Exception {
    final String T_Auth = "T_Authoria";
    final String P_Auth = "p_Authoria";
    insertTaxonWithAuthorship(targetKey, T_Auth, T_Animalia, Rank.GENUS, "Authoria", auth("Smith", "1850"), null);
    insertTaxonWithAuthorship(PROJECT_KEY, P_Auth, null, Rank.GENUS, "Authoria", auth("Jones", "1999"), T_Auth);

    setAuthorshipUpdate(Sector.AuthorshipUpdate.MISSING);
    runHierarchySync();

    Name pn = getName(PROJECT_KEY, P_Auth);
    assertNotNull(pn);
    assertTrue("existing authorship must be kept", pn.getAuthorship().contains("Jones"));
    assertFalse("source authorship must not be applied in MISSING mode when one exists", pn.getAuthorship().contains("Smith"));
  }

  /**
   * Phase 4 with {@link Sector.AuthorshipUpdate#ALWAYS}: an existing project authorship is
   * overwritten with the source's whenever the source has one.
   */
  @Test
  public void alwaysModeOverwritesExistingAuthorship() throws Exception {
    final String T_Auth = "T_Authoria";
    final String P_Auth = "p_Authoria";
    insertTaxonWithAuthorship(targetKey, T_Auth, T_Animalia, Rank.GENUS, "Authoria", auth("Smith", "1850"), null);
    insertTaxonWithAuthorship(PROJECT_KEY, P_Auth, null, Rank.GENUS, "Authoria", auth("Jones", "1999"), T_Auth);

    setAuthorshipUpdate(Sector.AuthorshipUpdate.ALWAYS);
    runHierarchySync();

    Name pn = getName(PROJECT_KEY, P_Auth);
    assertNotNull(pn);
    assertTrue("ALWAYS should apply the source authorship", pn.getAuthorship().contains("Smith"));
    assertFalse("ALWAYS should drop the previous authorship", pn.getAuthorship().contains("Jones"));
    assertAuthorshipSecondarySource(PROJECT_KEY, P_Auth, targetKey, T_Auth);
  }

  /**
   * Phase 4 with the default {@link Sector.AuthorshipUpdate#NONE}: authorship is never touched.
   */
  @Test
  public void noneModeLeavesAuthorshipUntouched() throws Exception {
    final String T_Auth = "T_Authoria";
    final String P_Auth = "p_Authoria";
    insertTaxonWithAuthorship(targetKey, T_Auth, T_Animalia, Rank.GENUS, "Authoria", auth("Smith", "1850"), null);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Auth, null, Rank.GENUS, "Authoria", T_Auth);

    // sector defaults to AuthorshipUpdate.NONE
    runHierarchySync();

    Name pn = getName(PROJECT_KEY, P_Auth);
    assertNotNull(pn);
    assertFalse("NONE mode must not add an authorship", pn.hasAuthorship());
  }

  /**
   * Project-side dedup: when the project already provides an (accepted) genus that is reached as an
   * ancestor of an id-matched species, the sync reuses it instead of importing a duplicate, and the
   * reused genus stays untagged by the sector. The species nests under the existing project genus.
   */
  @Test
  public void dedupReusesExistingProjectGenus() throws Exception {
    final String T_Rosaceae = "T_Rosaceae";
    final String T_Alchemilla = "T_Alchemilla";
    final String T_Alch_vulgaris = "T_Alch_vulgaris";
    final String P_Alchemilla = "p_Alchemilla";
    final String P_Alch_vulgaris = "p_Alch_vulgaris";

    // Source: Animalia > Rosaceae > Alchemilla > Alchemilla vulgaris
    insertTaxon(targetKey, T_Rosaceae, T_Animalia, Rank.FAMILY, "Rosaceae");
    insertTaxon(targetKey, T_Alchemilla, T_Rosaceae, Rank.GENUS, "Alchemilla");
    insertTaxon(targetKey, T_Alch_vulgaris, T_Alchemilla, Rank.SPECIES, "Alchemilla vulgaris");

    // Project: genus Alchemilla WITHOUT identifier (must be reused by name), species id-matched to source.
    insertTaxon(PROJECT_KEY, P_Alchemilla, null, Rank.GENUS, "Alchemilla");
    insertTaxonWithIdentifier(PROJECT_KEY, P_Alch_vulgaris, P_Alchemilla, Rank.SPECIES, "Alchemilla vulgaris", T_Alch_vulgaris);

    // names must be matched to the names index for the postgres matcher to find candidates
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    // exactly one Alchemilla genus in the project — the original, reused (no duplicate import)
    List<NameUsageBase> alch = listByName(PROJECT_KEY, Rank.GENUS, "Alchemilla");
    assertEquals("expected exactly one Alchemilla genus", 1, alch.size());
    assertEquals(P_Alchemilla, alch.get(0).getId());
    assertNull("reused project genus must not be tagged with the sector", alch.get(0).getSectorKey());

    // Rosaceae + Animalia were imported (sector-tagged)
    NameUsageBase rosaceae = getByName(PROJECT_KEY, Rank.FAMILY, "Rosaceae");
    assertNotNull(rosaceae);
    assertEquals(hierarchySector.getId(), rosaceae.getSectorKey());
    NameUsageBase animalia = getByName(PROJECT_KEY, Rank.KINGDOM, "Animalia");
    assertNotNull(animalia);
    assertEquals(hierarchySector.getId(), animalia.getSectorKey());

    // the id-matched species nests under the existing project genus (not under the family)
    NameUsageBase pAV = getByID(PROJECT_KEY, P_Alch_vulgaris);
    assertEquals("species should nest under the existing project genus", P_Alchemilla, pAV.getParentId());
  }

  /**
   * Name-match fallback, HIGHERRANK: a floating species with no source identifier whose species is
   * absent from the source is placed under the genus the matcher resolves, with the genus imported.
   * The placed usage is flagged MATCHING_HIGHERRANK and re-runs stay idempotent.
   */
  @Test
  public void nameMatchHigherRankPlacesUnderGenus() throws Exception {
    final String T_Rosaceae = "T_Rosaceae";
    final String T_Alchemilla = "T_Alchemilla";
    final String P_floating = "p_alch_acutiloba";

    // Source: Animalia > Rosaceae > Alchemilla (no species in source)
    insertTaxon(targetKey, T_Rosaceae, T_Animalia, Rank.FAMILY, "Rosaceae");
    insertTaxon(targetKey, T_Alchemilla, T_Rosaceae, Rank.GENUS, "Alchemilla");

    // Project: floating species at root, no identifier
    insertTaxon(PROJECT_KEY, P_floating, null, Rank.SPECIES, "Alchemilla acutiloba");

    // reset the shared in-memory nidx so stale IDs from prior tests do not cause FK violations
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase genus = getByName(PROJECT_KEY, Rank.GENUS, "Alchemilla");
    assertNotNull("genus Alchemilla should have been imported", genus);
    assertEquals(hierarchySector.getId(), genus.getSectorKey());

    NameUsageBase floating = getByID(PROJECT_KEY, P_floating);
    assertEquals("floating species should nest under the imported genus", genus.getId(), floating.getParentId());
    assertTrue("placed species should stay accepted", floating.getStatus().isTaxon());
    assertNull("placed species must not gain an identifier", floating.getIdentifier());
    assertHasVerbatimIssue(PROJECT_KEY, P_floating, Issue.MATCHING_HIGHERRANK);

    // idempotency: a second run keeps a single genus and a single issue
    runHierarchySync();
    assertEquals(1, listByName(PROJECT_KEY, Rank.GENUS, "Alchemilla").size());
    assertEquals(1, verbatimIssueCount(PROJECT_KEY, P_floating, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * Full name match: a species that exists in the source but was never id-matched is placed under its genus
   * (imported) and treated like an identifier match - it gains the source identifier and is no higher rank
   * placement. See backend#1582.
   */
  @Test
  public void nameMatchFullMatchPlacesUnderGenus() throws Exception {
    final String T_Caryo = "T_Caryophyllaceae";
    final String T_Agrostemma = "T_Agrostemma";
    final String T_Ag_githago = "T_Ag_githago";
    final String P_floating = "p_ag_githago";

    insertTaxon(targetKey, T_Caryo, T_Animalia, Rank.FAMILY, "Caryophyllaceae");
    insertTaxon(targetKey, T_Agrostemma, T_Caryo, Rank.GENUS, "Agrostemma");
    insertTaxon(targetKey, T_Ag_githago, T_Agrostemma, Rank.SPECIES, "Agrostemma githago");

    insertTaxon(PROJECT_KEY, P_floating, null, Rank.SPECIES, "Agrostemma githago");

    // reset the shared in-memory nidx so stale IDs from prior tests do not cause FK violations
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase genus = getByName(PROJECT_KEY, Rank.GENUS, "Agrostemma");
    assertNotNull("genus Agrostemma should have been imported", genus);
    NameUsageBase floating = getByID(PROJECT_KEY, P_floating);
    assertEquals("floating species should nest under its genus", genus.getId(), floating.getParentId());
    assertHasIdentifier(floating, SCOPE, T_Ag_githago);
    assertEquals("a full match is no higher rank placement", 0,
      verbatimIssueCount(PROJECT_KEY, P_floating, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * Name-match fallback, ambiguous: when the source has two genera sharing the same canonical name,
   * the higher-rank match is AMBIGUOUS and the floating species is left at the root, unflagged.
   */
  @Test
  public void nameMatchAmbiguousLeavesUsageUntouched() throws Exception {
    final String T_FamA = "T_FamA";
    final String T_FamB = "T_FamB";
    final String T_GenusA = "T_GenusA";
    final String T_GenusB = "T_GenusB";
    final String P_floating = "p_dupgenus_spec";

    // two genera "Dupgenus" under different families => higher match is ambiguous
    insertTaxon(targetKey, T_FamA, T_Animalia, Rank.FAMILY, "Aaaaceae");
    insertTaxon(targetKey, T_FamB, T_Animalia, Rank.FAMILY, "Bbbbceae");
    insertTaxon(targetKey, T_GenusA, T_FamA, Rank.GENUS, "Dupgenus");
    insertTaxon(targetKey, T_GenusB, T_FamB, Rank.GENUS, "Dupgenus");

    insertTaxon(PROJECT_KEY, P_floating, null, Rank.SPECIES, "Dupgenus specia");

    // reset the shared in-memory nidx so stale IDs from prior tests do not cause FK violations
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase floating = getByID(PROJECT_KEY, P_floating);
    assertNull("ambiguous floating species should stay at the root", floating.getParentId());
    assertEquals("ambiguous floating species must not be flagged", 0,
      verbatimIssueCount(PROJECT_KEY, P_floating, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * Name-match fallback ignores synonyms: a floating synonym whose name matches a source genus is
   * not re-parented (its parent stays its accepted taxon).
   */
  @Test
  public void nameMatchSkipsSynonyms() throws Exception {
    final String T_Caryo = "T_Caryophyllaceae";
    final String T_Agrostemma = "T_Agrostemma";
    final String P_acc = "p_acc_genus";
    final String P_syn = "p_syn_under_acc";

    insertTaxon(targetKey, T_Caryo, T_Animalia, Rank.FAMILY, "Caryophyllaceae");
    insertTaxon(targetKey, T_Agrostemma, T_Caryo, Rank.GENUS, "Agrostemma");

    insertTaxon(PROJECT_KEY, P_acc, null, Rank.GENUS, "Somegenus");
    insertSynonym(PROJECT_KEY, P_syn, P_acc, Rank.SPECIES, "Agrostemma githago");

    // reset the shared in-memory nidx so stale IDs from prior tests do not cause FK violations
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase syn = getByID(PROJECT_KEY, P_syn);
    assertTrue("synonym should stay a synonym", syn.getStatus().isSynonym());
    assertEquals("synonym parent must be unchanged", P_acc, syn.getParentId());
  }

  /**
   * Idempotency of name-match placement under a STABLE (deduped) project genus. The project provides
   * an existing accepted genus "Alchemilla" with NO identifier (dedup reuses it by name) and a
   * floating accepted species "Alchemilla mollis" at root with NO identifier. The genus is NOT
   * tagged by the sector and therefore survives {@code deleteBySector}. A second sync run must:
   * (a) still find exactly one "Alchemilla" genus with the original id and no sector tag, (b) leave
   * the floating species' parentId unchanged, and (c) produce exactly one MATCHING_HIGHERRANK issue
   * (no duplication from re-flagging the already-correctly-placed usage).
   */
  @Test
  public void nameMatchHigherRankIdempotentUnderDedupedGenus() throws Exception {
    final String T_Rosaceae = "T_Rosaceae";
    final String T_Alchemilla = "T_Alchemilla";
    final String P_Alchemilla = "p_Alchemilla";
    final String P_floating = "p_alch_mollis";

    // Source: Animalia > Rosaceae > Alchemilla (no species in source, so floating species gets a
    // HIGHERRANK match to the genus Alchemilla).
    insertTaxon(targetKey, T_Rosaceae, T_Animalia, Rank.FAMILY, "Rosaceae");
    insertTaxon(targetKey, T_Alchemilla, T_Rosaceae, Rank.GENUS, "Alchemilla");

    // Project: an existing accepted genus "Alchemilla" WITHOUT identifier (dedup will reuse it by
    // name, and deleteBySector will NOT wipe it since it has no sector tag), plus a floating
    // accepted species at root WITHOUT identifier.
    insertTaxon(PROJECT_KEY, P_Alchemilla, null, Rank.GENUS, "Alchemilla");
    insertTaxon(PROJECT_KEY, P_floating, null, Rank.SPECIES, "Alchemilla mollis");

    // reset the shared in-memory nidx so stale IDs from prior tests do not cause FK violations
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    // Run 1 — initial placement
    runHierarchySync();

    List<NameUsageBase> alch = listByName(PROJECT_KEY, Rank.GENUS, "Alchemilla");
    assertEquals("expected exactly one Alchemilla genus after run 1", 1, alch.size());
    assertEquals("genus should be the original project entry (deduped, not imported)", P_Alchemilla, alch.get(0).getId());
    assertNull("reused project genus must not be tagged with the sector", alch.get(0).getSectorKey());

    NameUsageBase floating = getByID(PROJECT_KEY, P_floating);
    assertEquals("floating species should nest under the existing project genus after run 1", P_Alchemilla, floating.getParentId());

    // Run 2 — exercises the "unchanged → still flag" idempotency branch in placeNameMatches
    runHierarchySync();

    alch = listByName(PROJECT_KEY, Rank.GENUS, "Alchemilla");
    assertEquals("second run must not duplicate the Alchemilla genus", 1, alch.size());
    assertEquals("genus id must remain the original project entry after second run", P_Alchemilla, alch.get(0).getId());
    assertNull("reused genus must still not be tagged with the sector after second run", alch.get(0).getSectorKey());

    floating = getByID(PROJECT_KEY, P_floating);
    assertEquals("floating species parent must be unchanged after second run", P_Alchemilla, floating.getParentId());
    assertEquals("MATCHING_HIGHERRANK should appear exactly once after the second run (no duplication)",
      1, verbatimIssueCount(PROJECT_KEY, P_floating, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * An unranked project usage is never name-placed. UsageMatcher skips its rank filter outright for an
   * unranked query, so such a name matches any canonical homonym at any rank - which put a project's
   * unranked container "Biota" under the plant genus Platycladus.
   * https://github.com/CatalogueOfLife/backend/issues/1575
   */
  @Test
  public void nameMatchSkipsUnrankedNames() throws Exception {
    final String T_Bioticaceae = "T_Bioticaceae";
    final String T_Biotica = "T_Biotica";
    final String P_container = "p_biotica_container";

    // source holds a genus of that name in an unrelated lineage
    insertTaxon(targetKey, T_Bioticaceae, T_Animalia, Rank.FAMILY, "Bioticaceae");
    insertTaxon(targetKey, T_Biotica, T_Bioticaceae, Rank.GENUS, "Biotica");

    // project holds a hand made unranked container of the same name at the root
    insertTaxon(PROJECT_KEY, P_container, null, Rank.UNRANKED, "Biotica");

    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase container = getByID(PROJECT_KEY, P_container);
    assertNull("an unranked container must never be re-parented by a name match", container.getParentId());
    assertEquals(0, verbatimIssueCount(PROJECT_KEY, P_container, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * A taxon other sectors attach into is a structural anchor of the project. Moving it drags every
   * sector's output along, so the name-match fallback must leave it alone even when it matches cleanly.
   */
  @Test
  public void nameMatchSkipsSectorTargets() throws Exception {
    final String T_Anchorfam = "T_Anchorfam";
    final String T_Anchorgenus = "T_Anchorgenus";
    final String P_target = "p_anchor_target";

    insertTaxon(targetKey, T_Anchorfam, T_Animalia, Rank.FAMILY, "Anchoridae");
    insertTaxon(targetKey, T_Anchorgenus, T_Anchorfam, Rank.GENUS, "Anchorgenus");

    insertTaxon(PROJECT_KEY, P_target, null, Rank.GENUS, "Anchorgenus");
    createAttachSectorWithTarget(getByID(PROJECT_KEY, P_target));

    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase target = getByID(PROJECT_KEY, P_target);
    assertNull("a sector target must never be re-parented by a name match", target.getParentId());
    assertEquals(0, verbatimIssueCount(PROJECT_KEY, P_target, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * A source identifier the source no longer knows must not shadow the name-match fallback: sources
   * delete and reissue ids, and trusting the mere presence of one leaves the usage unplaced forever.
   */
  @Test
  public void nameMatchRescuesStaleIdentifier() throws Exception {
    final String T_Stalefam = "T_Stalefam";
    final String T_Stalegenus = "T_Stalegenus";
    final String T_Stalesp = "T_Stalesp";
    final String P_floating = "p_stale_species";

    insertTaxon(targetKey, T_Stalefam, T_Animalia, Rank.FAMILY, "Staleidae");
    insertTaxon(targetKey, T_Stalegenus, T_Stalefam, Rank.GENUS, "Stalegenus");
    insertTaxon(targetKey, T_Stalesp, T_Stalegenus, Rank.SPECIES, "Stalegenus specia");

    // the project still points at an id the source has since deleted
    insertTaxonWithIdentifier(PROJECT_KEY, P_floating, null, Rank.SPECIES, "Stalegenus specia", "T_DELETED_BY_SOURCE");

    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    NameUsageBase genus = getByName(PROJECT_KEY, Rank.GENUS, "Stalegenus");
    assertNotNull("genus should have been imported for the name match", genus);
    NameUsageBase floating = getByID(PROJECT_KEY, P_floating);
    assertEquals("a usage with a stale identifier must be placed by name instead", genus.getId(), floating.getParentId());
    assertHasIdentifier(floating, SCOPE, T_Stalesp);

    // the next run follows the identifier that still resolves and adds no further one
    runHierarchySync();
    assertEquals(2, getByID(PROJECT_KEY, P_floating).getIdentifier().size());
  }

  /**
   * The floating usage is matched with its own project classification, so the matcher can apply its
   * taxonomic group filter. Without it a zoological name silently matches its botanical homonym and the
   * usage is re-parented into the wrong kingdom.
   */
  @Test
  public void nameMatchRejectsDisparateKingdom() throws Exception {
    final String T_Plantae = "T_Plantae";
    final String T_Lamiaceae = "T_Lamiaceae";
    final String T_Homonymus = "T_Homonymus";
    final String P_Animalia = "p_Animalia";
    final String P_floating = "p_homonymus_specia";

    // the source knows this genus only as a plant
    insertTaxon(targetKey, T_Plantae, null, Rank.KINGDOM, "Plantae");
    insertTaxon(targetKey, T_Lamiaceae, T_Plantae, Rank.FAMILY, "Lamiaceae");
    insertTaxon(targetKey, T_Homonymus, T_Lamiaceae, Rank.GENUS, "Homonymus");

    // the project knows it as an animal
    insertTaxon(PROJECT_KEY, P_Animalia, null, Rank.KINGDOM, "Animalia");
    insertTaxon(PROJECT_KEY, P_floating, P_Animalia, Rank.SPECIES, "Homonymus specia");

    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);

    runHierarchySync();

    // the genus candidate is rejected as a different taxonomic group, so the walk up settles on the
    // kingdom the usage already sits under - a no-op placement rather than a jump into the plants
    NameUsageBase floating = getByID(PROJECT_KEY, P_floating);
    assertEquals("an animal must not be placed under its botanical homonym", P_Animalia, floating.getParentId());
    assertNull("the botanical genus must not have been imported", getByName(PROJECT_KEY, Rank.GENUS, "Homonymus"));
    assertNull("no part of the botanical lineage may be imported", getByName(PROJECT_KEY, Rank.FAMILY, "Lamiaceae"));
  }

  // ---------- a synonym of an accepted taxon the project lacks, backend#1582 ----------

  static final String T_Planorbidae = "T_Planorbidae";
  static final String T_Armiger = "T_Armiger";
  static final String T_Arm_crista = "T_Arm_crista";
  static final String T_Gyr_crista = "T_Gyr_crista";
  static final String P_Gyr_crista = "p_Gyr_crista";

  /**
   * The project name carries the id of a source synonym whose accepted taxon the project does not hold.
   * The accepted has to be imported and the project name demoted to its synonym. It used to stay accepted
   * and was moved under the accepted's genus instead: Gyraulus crista under Armiger in the Archis project.
   * https://github.com/CatalogueOfLife/backend/issues/1582
   */
  @Test
  public void idMatchedSynonymOfMissingAcceptedIsDemoted() throws Exception {
    populateArmigerCrista(null);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista", T_Gyr_crista);

    runHierarchySync();

    assertDemotedToImportedArmigerCrista();
  }

  /**
   * A re-run deletes the imported accepted the demoted synonym points at. The synonym is then a project
   * synonym of a source synonym and has to be retargeted to the re-imported accepted.
   */
  @Test
  public void demotionToImportedAcceptedSurvivesRerun() throws Exception {
    populateArmigerCrista(null);
    insertTaxonWithIdentifier(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista", T_Gyr_crista);

    runHierarchySync();
    int taxa = countDataset(PROJECT_KEY, false);
    int synonyms = countDataset(PROJECT_KEY, true);

    runHierarchySync();

    assertEquals("a second sync must not duplicate accepted taxa", taxa, countDataset(PROJECT_KEY, false));
    assertEquals("a second sync must not duplicate synonyms", synonyms, countDataset(PROJECT_KEY, true));
    assertDemotedToImportedArmigerCrista();
  }

  /**
   * A full name match is as good as an identifier: without one the name is demoted all the same, and it
   * gains the source identifier so the next run can find the synonym it became.
   */
  @Test
  public void exactNameMatchSynonymOfMissingAcceptedIsDemoted() throws Exception {
    populateArmigerCrista(null);
    insertTaxon(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista");
    rematchNames();

    runHierarchySync();

    assertDemotedToImportedArmigerCrista();
    assertHasIdentifier(getByID(PROJECT_KEY, P_Gyr_crista), SCOPE, T_Gyr_crista);
    assertEquals("a full match is no higher rank placement", 0,
      verbatimIssueCount(PROJECT_KEY, P_Gyr_crista, Issue.MATCHING_HIGHERRANK));
  }

  /**
   * The source synonym carries an authorship the project name lacks - the shape of the Archis text tree names.
   */
  @Test
  public void variantNameMatchSynonymOfMissingAcceptedIsDemoted() throws Exception {
    populateArmigerCrista(auth("Linnaeus", "1758"));
    insertTaxon(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista");
    rematchNames();

    runHierarchySync();

    assertDemotedToImportedArmigerCrista();
    assertHasIdentifier(getByID(PROJECT_KEY, P_Gyr_crista), SCOPE, T_Gyr_crista);
  }

  /**
   * Once demoted a name-matched usage is a synonym and never name matched again, so only the identifier it
   * gained keeps it attached to the accepted that the re-run imports anew.
   */
  @Test
  public void nameMatchedDemotionSurvivesRerun() throws Exception {
    populateArmigerCrista(null);
    insertTaxon(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista");
    rematchNames();

    runHierarchySync();
    runHierarchySync();

    assertDemotedToImportedArmigerCrista();
    assertEquals("a re-run must not add the identifier again", 1, getByID(PROJECT_KEY, P_Gyr_crista).getIdentifier().size());
  }

  /**
   * The accepted exists in the project already, without any identifier. It is reused, not imported again.
   */
  @Test
  public void demotesToExistingProjectAccepted() throws Exception {
    final String P_Armiger = "p_Armiger";
    final String P_Arm_crista = "p_Arm_crista";
    populateArmigerCrista(null);
    insertTaxon(PROJECT_KEY, P_Armiger, null, Rank.GENUS, "Armiger");
    insertTaxon(PROJECT_KEY, P_Arm_crista, P_Armiger, Rank.SPECIES, "Armiger crista");
    insertTaxonWithIdentifier(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista", T_Gyr_crista);
    rematchNames();

    runHierarchySync();

    NameUsageBase accepted = getByName(PROJECT_KEY, Rank.SPECIES, "Armiger crista");
    assertEquals("the existing project Armiger crista must be reused", P_Arm_crista, accepted.getId());
    assertNull(accepted.getSectorKey());
    NameUsageBase gyr = getByID(PROJECT_KEY, P_Gyr_crista);
    assertTrue(gyr.getStatus().isSynonym());
    assertEquals(P_Arm_crista, gyr.getParentId());
  }

  /**
   * An infraspecific accepted whose species the project does not hold goes under the genus. Its direct source
   * parent is never imported, so insertion order and parent have to follow the source chain.
   */
  @Test
  public void importedInfraspecificAcceptedNestsUnderGenus() throws Exception {
    final String T_Arm_crista_cristata = "T_Arm_crista_cristata";
    final String T_Gyr_crista_cristata = "T_Gyr_crista_cristata";
    final String P_Gyr_crista_cristata = "p_Gyr_crista_cristata";
    populateArmigerCrista(null);
    insertTaxon(targetKey, T_Arm_crista_cristata, T_Arm_crista, Rank.SUBSPECIES, "Armiger crista cristata");
    insertSynonym(targetKey, T_Gyr_crista_cristata, T_Arm_crista_cristata, Rank.SUBSPECIES, "Gyraulus crista cristata");
    insertTaxonWithIdentifier(PROJECT_KEY, P_Gyr_crista_cristata, null, Rank.SUBSPECIES, "Gyraulus crista cristata", T_Gyr_crista_cristata);

    runHierarchySync();

    NameUsageBase armiger = getByName(PROJECT_KEY, Rank.GENUS, "Armiger");
    assertNotNull(armiger);
    NameUsageBase accepted = getByName(PROJECT_KEY, Rank.SUBSPECIES, "Armiger crista cristata");
    assertNotNull("the missing infraspecific accepted should have been imported", accepted);
    assertEquals("it nests under the genus, as its species is not in the project", armiger.getId(), accepted.getParentId());
    assertNull("the species is not imported", getByName(PROJECT_KEY, Rank.SPECIES, "Armiger crista"));
    NameUsageBase gyr = getByID(PROJECT_KEY, P_Gyr_crista_cristata);
    assertTrue(gyr.getStatus().isSynonym());
    assertEquals(accepted.getId(), gyr.getParentId());
  }

  /**
   * Guard for the promotion of name matches: an authorship conflict removes the species candidate, so the
   * usage falls back to a higher rank placement and keeps its status.
   */
  @Test
  public void authorshipConflictStaysPlacementOnly() throws Exception {
    populateArmigerCrista(auth("Linnaeus", "1758"));
    insertTaxon(targetKey, "T_Gyraulus", T_Planorbidae, Rank.GENUS, "Gyraulus");
    insertTaxonWithAuthorship(PROJECT_KEY, P_Gyr_crista, null, Rank.SPECIES, "Gyraulus crista", auth("Smith", "1900"), null);
    rematchNames();

    runHierarchySync();

    NameUsageBase gyraulus = getByName(PROJECT_KEY, Rank.GENUS, "Gyraulus");
    assertNotNull("the genus should have been imported for the higher rank match", gyraulus);
    NameUsageBase gyr = getByID(PROJECT_KEY, P_Gyr_crista);
    assertTrue("a higher rank placement must not change the status", gyr.getStatus().isTaxon());
    assertEquals(gyraulus.getId(), gyr.getParentId());
    assertNull("a higher rank placement must not gain an identifier", gyr.getIdentifier());
    assertHasVerbatimIssue(PROJECT_KEY, P_Gyr_crista, Issue.MATCHING_HIGHERRANK);
    assertNull("nothing may be imported for the conflicting species", getByName(PROJECT_KEY, Rank.SPECIES, "Armiger crista"));
  }

  /**
   * Source: Animalia > Planorbidae > Armiger > Armiger crista, with Gyraulus crista as its synonym.
   * An authorship on the synonym makes an authorless project name a VARIANT rather than an EXACT match.
   */
  private static void populateArmigerCrista(Authorship synonymAuthorship) {
    insertTaxon(targetKey, T_Planorbidae, T_Animalia, Rank.FAMILY, "Planorbidae");
    insertTaxon(targetKey, T_Armiger, T_Planorbidae, Rank.GENUS, "Armiger");
    insertTaxon(targetKey, T_Arm_crista, T_Armiger, Rank.SPECIES, "Armiger crista");
    insertSynonymWithAuthorship(targetKey, T_Gyr_crista, T_Arm_crista, Rank.SPECIES, "Gyraulus crista", synonymAuthorship);
  }

  private void assertDemotedToImportedArmigerCrista() {
    NameUsageBase armiger = getByName(PROJECT_KEY, Rank.GENUS, "Armiger");
    assertNotNull("genus Armiger should have been imported", armiger);
    NameUsageBase accepted = getByName(PROJECT_KEY, Rank.SPECIES, "Armiger crista");
    assertNotNull("the missing accepted Armiger crista should have been imported", accepted);
    assertTrue(accepted.getStatus().isTaxon());
    assertEquals(hierarchySector.getId(), accepted.getSectorKey());
    assertEquals("the imported accepted should nest under its genus", armiger.getId(), accepted.getParentId());
    assertHasIdentifier(accepted, SCOPE, T_Arm_crista);
    assertVerbatimSource(accepted, targetKey, T_Arm_crista);

    NameUsageBase gyr = getByID(PROJECT_KEY, P_Gyr_crista);
    assertTrue("Gyraulus crista should have been demoted to a synonym", gyr.getStatus().isSynonym());
    assertEquals("Gyraulus crista should be a synonym of the imported Armiger crista", accepted.getId(), gyr.getParentId());
    assertEquals("the demoted name must not be copied in again as a synonym", 1,
      listByName(PROJECT_KEY, Rank.SPECIES, "Gyraulus crista").size());
  }

  /** Names must be matched to the names index for the postgres matcher to find candidates. */
  private void rematchNames() {
    // reset the shared in-memory nidx so stale IDs from prior tests do not cause FK violations
    NameMatchingRule.getIndex().reset();
    matchingRule.rematch(targetKey);
    matchingRule.rematch(PROJECT_KEY);
  }

  // ---------- references to imported usages across re-syncs ----------

  /**
   * A re-sync deletes what the sector imported and imports it again. It has to come back under the same ids,
   * or everything pointing at the imported usages - children, synonyms, sector targets - dangles.
   */
  @Test
  public void resyncKeepsIdsOfImportedUsages() throws Exception {
    runHierarchySync();
    String felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae").getId();
    String silvestris = getByName(PROJECT_KEY, Rank.SPECIES, "Felis silvestris").getId();

    runHierarchySync();

    assertEquals("an imported ancestor must keep its id", felidae, getByName(PROJECT_KEY, Rank.FAMILY, "Felidae").getId());
    assertEquals("a copied synonym must keep its id", silvestris, getByName(PROJECT_KEY, Rank.SPECIES, "Felis silvestris").getId());
  }

  /**
   * A curator places a taxon under an imported ancestor. The next sync must not leave it pointing at a deleted row.
   */
  @Test
  public void usageUnderImportedTaxonSurvivesResync() throws Exception {
    runHierarchySync();
    String felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae").getId();
    insertTaxon(PROJECT_KEY, P_Custom, felidae, Rank.GENUS, "Customia");

    runHierarchySync();

    NameUsageBase custom = getByID(PROJECT_KEY, P_Custom);
    assertEquals(felidae, custom.getParentId());
    assertNotNull("the parent must still exist", getByID(PROJECT_KEY, custom.getParentId()));
    assertEquals(0, verbatimIssueCount(PROJECT_KEY, P_Custom, Issue.PARENT_ID_INVALID));
  }

  /**
   * A merge sector adds vernacular names to usages another sector owns - the imported ancestors included.
   * They carry the merge sector's key, so the hierarchy sync neither deletes nor copies them, and they must survive.
   */
  @Test
  public void foreignVernacularOnImportedTaxonSurvivesResync() throws Exception {
    runHierarchySync();
    String felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae").getId();
    Sector merge = createMergeSector();
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      VernacularName vn = new VernacularName();
      vn.setDatasetKey(PROJECT_KEY);
      vn.setSectorKey(merge.getId());
      vn.setName("Katzen");
      vn.setLanguage("deu");
      vn.applyUser(USER);
      s.getMapper(VernacularNameMapper.class).create(vn, felidae);
    }

    runHierarchySync();

    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var vnames = s.getMapper(VernacularNameMapper.class).listByTaxon(DSID.of(PROJECT_KEY, felidae));
      assertEquals("the merge sector's vernacular must still be attached to Felidae", 1, vnames.size());
      assertEquals("Katzen", vnames.get(0).getName());
    }
  }

  /**
   * The source dropped a taxon the project still refers to. Whatever is left pointing at the deleted import loses its
   * parent and is flagged, instead of dangling: a missing parent aborts releases and a synonym without accepted aborts
   * the search index.
   */
  @Test
  public void referencesToDroppedImportsAreRepaired() throws Exception {
    runHierarchySync();
    String felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae").getId();
    insertTaxon(PROJECT_KEY, P_Custom, felidae, Rank.GENUS, "Customia");
    insertSynonym(PROJECT_KEY, P_Custom_syn, felidae, Rank.FAMILY, "Customiidae");
    // the source dissolves Felidae
    setParent(targetKey, T_Felis, T_Animalia);
    deleteUsage(targetKey, T_Felidae);

    HierarchySync sync = runHierarchySync();

    NameUsageBase custom = getByID(PROJECT_KEY, P_Custom);
    assertNull("a taxon under the dropped import must lose its parent", custom.getParentId());
    assertHasVerbatimIssue(PROJECT_KEY, P_Custom, Issue.PARENT_ID_INVALID);
    NameUsageBase syn = getByID(PROJECT_KEY, P_Custom_syn);
    assertNull("a synonym of the dropped import must lose its accepted", syn.getParentId());
    assertHasVerbatimIssue(PROJECT_KEY, P_Custom_syn, Issue.ACCEPTED_ID_INVALID);
    assertTrue("the sync should warn about the repair: " + sync.getState().getWarnings(),
      sync.getState().getWarnings().stream().anyMatch(w -> w.startsWith("2 usages pointed at a parent that no longer exists")));
  }

  /**
   * A sync that fails after deleting its imports must still repair what points at them.
   */
  @Test
  public void failedSyncStillRepairsDanglingParents() throws Exception {
    runHierarchySync();
    String felidae = getByName(PROJECT_KEY, Rank.FAMILY, "Felidae").getId();
    insertTaxon(PROJECT_KEY, P_Custom, felidae, Rank.GENUS, "Customia");

    // the source matcher is first needed right after the old imports are gone
    HierarchySync sync = newHierarchySync((dk, session) -> {
      throw new IllegalStateException("source matcher unavailable");
    });
    sync.run();
    assertEquals(JobStatus.FAILED, sync.getStatus());

    NameUsageBase custom = getByID(PROJECT_KEY, P_Custom);
    assertNull(custom.getParentId());
    assertHasVerbatimIssue(PROJECT_KEY, P_Custom, Issue.PARENT_ID_INVALID);
  }

  static final String P_Custom = "p_custom";
  static final String P_Custom_syn = "p_custom_syn";

  // ---------- helpers ----------

  private static Sector createMergeSector() {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(Sector.Mode.MERGE);
      sector.setDatasetKey(PROJECT_KEY);
      sector.setSubjectDatasetKey(targetKey);
      sector.applyUser(USER);
      s.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

  private static void deleteUsage(int datasetKey, String id) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      s.getMapper(NameUsageMapper.class).delete(DSID.of(datasetKey, id));
    }
  }

  private static void insertSynonymWithAuthorship(int datasetKey, String id, String acceptedId, Rank rank, String scientificName,
                                                  Authorship combinationAuthorship) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = buildName(datasetKey, id, scientificName, rank);
      if (combinationAuthorship != null) {
        n.setCombinationAuthorship(combinationAuthorship);
        n.rebuildAuthorship();
      }
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

  private static Sector createAttachSectorWithTarget(NameUsageBase target) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(Sector.Mode.ATTACH);
      sector.setDatasetKey(PROJECT_KEY);
      sector.setSubjectDatasetKey(targetKey);
      sector.setTarget(target.toSimpleNameLink());
      sector.applyUser(USER);
      s.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

  private HierarchySync runHierarchySync() throws Exception {
    HierarchySync sync = newHierarchySync(null);
    sync.run();
    if (sync.getStatus() != JobStatus.FINISHED) {
      throw new AssertionError("HierarchySync did not finish cleanly: status=" + sync.getStatus() + " error=" + sync.getState().getError());
    }
    return sync;
  }

  /**
   * @param sourceMatcherProvider replaces the postgres matcher against the source dataset if given
   */
  private HierarchySync newHierarchySync(BiFunction<Integer, SqlSession, UsageMatcher> sourceMatcherProvider) {
    SectorDao sdao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), null, null);
    SectorImportDao siDao = new SectorImportDao(SqlSessionFactoryRule.getSqlSessionFactory(), TreeRepoRule.getRepo());
    EventBroker bus = TestUtils.mockedBroker();
    // postgres matchers read live data and hold no resources beyond the session they are given
    UsageMatcherFactory matcherFactory = new UsageMatcherFactory(new MatchingConfig(), NameMatchingRule.getIndex(), SqlSessionFactoryRule.getSqlSessionFactory(), null);
    return new HierarchySync(
      hierarchySector,
      SqlSessionFactoryRule.getSqlSessionFactory(),
      session -> matcherFactory.postgres(PROJECT_KEY, session),
      sourceMatcherProvider != null ? sourceMatcherProvider : (dk, session) -> matcherFactory.postgres(dk, session),
      LatestDatasetKeyCache.passThru(),
      bus,
      NameUsageIndexService.passThru(),
      sdao,
      siDao,
      null,
      scopeResolver,
      USER
    );
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

  /** Repoints an existing usage - the only way to close a parent cycle past the (dataset_key, parent_id) FK. */
  private static void setParent(int datasetKey, String id, String parentId) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      s.getMapper(NameUsageMapper.class).updateParentId(DSID.of(datasetKey, id), parentId, USER);
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

  private static void insertSynonymWithIdentifier(int datasetKey, String id, String acceptedId, Rank rank, String scientificName, String targetId) {
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
      syn.setIdentifier(new java.util.ArrayList<>(List.of(new Identifier(SCOPE, targetId))));
      syn.applyUser(USER);
      s.getMapper(SynonymMapper.class).create(syn);
    }
  }

  /**
   * Inserts an accepted taxon whose name carries a parsed combination authorship. A target
   * identifier can optionally be attached (pass null to skip).
   */
  private static void insertTaxonWithAuthorship(int datasetKey, String id, String parentId, Rank rank, String scientificName,
                                                Authorship combinationAuthorship, String targetId) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = buildName(datasetKey, id, scientificName, rank);
      n.setCombinationAuthorship(combinationAuthorship);
      n.rebuildAuthorship();
      s.getMapper(NameMapper.class).create(n);
      Taxon t = buildTaxon(datasetKey, id, parentId, n, TaxonomicStatus.ACCEPTED);
      if (targetId != null) {
        t.setIdentifier(new java.util.ArrayList<>(List.of(new Identifier(SCOPE, targetId))));
      }
      s.getMapper(TaxonMapper.class).create(t);
    }
  }

  private static Authorship auth(String author, String year) {
    Authorship a = new Authorship();
    a.setAuthors(new java.util.ArrayList<>(List.of(author)));
    a.setYear(year);
    return a;
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

  /** Persists the authorship-update mode on the hierarchy sector (and mirrors it on the in-memory instance). */
  private void setAuthorshipUpdate(Sector.AuthorshipUpdate mode) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = s.getMapper(SectorMapper.class);
      Sector sec = sm.get(hierarchySector);
      sec.setAuthorshipUpdate(mode);
      sm.update(sec);
    }
    hierarchySector.setAuthorshipUpdate(mode);
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

  private static void assertVerbatimSource(NameUsageBase u, int expectedSourceDatasetKey, String expectedSourceId) {
    assertNotNull("usage should not be null", u);
    assertNotNull("usage " + u.getId() + " should carry a verbatim_source_key", u.getVerbatimSourceKey());
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var v = s.getMapper(life.catalogue.db.mapper.VerbatimSourceMapper.class)
        .get(DSID.of(u.getDatasetKey(), u.getVerbatimSourceKey()));
      assertNotNull("expected verbatim_source row for " + u.getId(), v);
      assertEquals("verbatim_source should record the source dataset", Integer.valueOf(expectedSourceDatasetKey), v.getSourceDatasetKey());
      assertEquals("verbatim_source.source_id should point at the original source record", expectedSourceId, v.getSourceId());
    }
  }

  private static Name getName(int datasetKey, String usageId) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return s.getMapper(NameMapper.class).getByUsage(datasetKey, usageId);
    }
  }

  private static void assertAuthorshipSecondarySource(int datasetKey, String usageId, int expectedSourceDatasetKey, String expectedSourceId) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = s.getMapper(NameMapper.class).getByUsage(datasetKey, usageId);
      assertNotNull("name should not be null", n);
      assertNotNull("name " + usageId + " should carry a verbatim_source_key", n.getVerbatimSourceKey());
      var sources = s.getMapper(VerbatimSourceMapper.class).getSources(DSID.of(datasetKey, n.getVerbatimSourceKey()));
      SecondarySource ss = sources.get(InfoGroup.AUTHORSHIP);
      assertNotNull("expected an AUTHORSHIP secondary source on " + usageId, ss);
      assertEquals("secondary source dataset", Integer.valueOf(expectedSourceDatasetKey), ss.getDatasetKey());
      assertEquals("secondary source id", expectedSourceId, ss.getId());
    }
  }

  private static void assertHasVerbatimIssue(int datasetKey, String usageId, Issue issue) {
    assertEquals("usage " + usageId + " should carry verbatim issue " + issue, 1,
      verbatimIssueCount(datasetKey, usageId, issue));
  }

  private static int verbatimIssueCount(int datasetKey, String usageId, Issue issue) {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      VerbatimSourceMapper vsm = s.getMapper(VerbatimSourceMapper.class);
      Integer vsKey = vsm.getVSKeyByUsage(DSID.of(datasetKey, usageId));
      if (vsKey == null) return 0;
      VerbatimSource v = vsm.getIssues(DSID.of(datasetKey, vsKey));
      if (v == null || v.getIssues() == null) return 0;
      return v.getIssues().contains(issue) ? 1 : 0;
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
