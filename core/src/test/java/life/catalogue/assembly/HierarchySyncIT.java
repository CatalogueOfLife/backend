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

import org.gbif.nameparser.api.Authorship;
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
   * carried that id and used the synonym as a parent_id).
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

    // The cycle guard must have blocked the demote of A — leaving A accepted with its original
    // (non-B) parent. The only forbidden outcome is A.parent_id == B, which would close the loop.
    assertNotEquals("P_A.parent_id must not equal P_B (would close a parent cycle)", P_B, a.getParentId());

    // No project-side cycle anywhere in this pair.
    if (P_A.equals(b.getParentId())) {
      assertNotEquals("If P_B still points at P_A, then P_A must not point back at P_B", P_B, a.getParentId());
    }
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
   * Name-match fallback, full match: a species that exists in the source but was never id-matched is
   * placed under its genus (imported) without becoming an identifier match (no status/synonym change).
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
    assertNull("placed species must not gain an identifier", floating.getIdentifier());
    assertHasVerbatimIssue(PROJECT_KEY, P_floating, Issue.MATCHING_HIGHERRANK);
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
        session -> matcherFactory.postgres(PROJECT_KEY, session),
        (dk, session) -> matcherFactory.postgres(dk, session),
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
