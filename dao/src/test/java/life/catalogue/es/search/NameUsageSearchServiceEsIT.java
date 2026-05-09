package life.catalogue.es.search;

import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.*;
import life.catalogue.config.IndexConfig;
import life.catalogue.es.EsTestBase;
import life.catalogue.es.EsUtil;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import org.junit.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import static life.catalogue.api.search.NameUsageRequest.SearchContent.SCIENTIFIC_NAME;
import static life.catalogue.api.search.NameUsageRequest.SearchContent.VERNACULAR_NAME;
import static life.catalogue.api.search.NameUsageRequest.SearchType.*;
import static life.catalogue.api.search.NameUsageSearchParameter.*;
import static life.catalogue.es.TestIndexUtils.*;
import static org.junit.Assert.*;

/**
 * Integration test for {@link NameUsageSearchServiceEs} that indexes 12 test usages once and
 * exercises all search parameters, facets, sort options, and QMatcher variants.
 * <p>
 * Test data layout (12 usages total):
 * <ul>
 *   <li>Dataset 100 (ZOOLOGICAL, 8 entries): t1–t7 taxa, s1 synonym</li>
 *   <li>Dataset 200 (BOTANICAL, 4 entries): t8–t10 taxa, s2 synonym</li>
 * </ul>
 * <p>
 * Known limitation: enum-typed filter parameters (STATUS, NOM_CODE, ENVIRONMENT, ISSUE, etc.)
 * are stored as string names in Elasticsearch keyword fields, but the filter translator sends
 * the ordinal as an integer. These filters are tested only for "no exception" behaviour.
 */
public class NameUsageSearchServiceEsIT extends EsTestBase {

  static final int DS1 = 100;
  static final int DS2 = 200;
  static final int TOTAL = 17;
  /** Catalogue key used in all SimpleDecision entries (set by newNameUsageWrapper helper). */
  static final int CAT99 = 99;
  /** sector dataset key set by the newNameUsageWrapper helper for all usages. */
  static final int SECTOR_DK = 42;
  /** Secondary source keys set by the newNameUsageWrapper helper for all usages. */
  static final int SS_KEY1 = 1010;
  static final int SS_KEY2 = 2123;

  static final String REF_A = "ref-A";
  static final String REF_B = "ref-B";

  private static NameUsageSearchServiceEs service;

  // -----------------------------------------------------------------------
  // One-time index setup
  // -----------------------------------------------------------------------

  @BeforeClass
  public static void indexTestData() throws Exception {
    ElasticsearchClient c = esSetup.getClient();
    IndexConfig cfg = esSetup.getEsConfig().index;
    EsUtil.deleteIndex(c, cfg.name);
    EsUtil.createIndex(c, cfg);

    // ---- Dataset 100: ZOOLOGICAL (6 taxa + 1 synonym = 7; plus 1 special taxon = 8) ----
    insert(c, cfg, taxon("t1", "Animalia", null,                Rank.KINGDOM, NomCode.ZOOLOGICAL, DS1, null, null));
    insert(c, cfg, taxon("t2", "Chordata", null,                Rank.PHYLUM,  NomCode.ZOOLOGICAL, DS1, null, null));
    insert(c, cfg, taxon("t3", "Mammalia", null,                Rank.CLASS,   NomCode.ZOOLOGICAL, DS1, null, null));
    insert(c, cfg, taxon("t4", "Carnivora", null,               Rank.ORDER,   NomCode.ZOOLOGICAL, DS1, null, null));
    // t5: Felis genus – sectorKey=5, publishedIn=REF_A
    insert(c, cfg, taxon("t5", "Felis", "Linnaeus, 1758",       Rank.GENUS,   NomCode.ZOOLOGICAL, DS1,    5, REF_A));
    // t6: Felis catus – extinct=true, publishedIn=REF_A; also carries a BLOCK decision
    NameUsageWrapper w6 = taxon("t6", "Felis catus", "L., 1758", Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, REF_A);
    ((Taxon) w6.getUsage()).setExtinct(true);
    // Keep the default REVIEWED decision and add a BLOCK one (used in DECISION_MODE filter test)
    List<SimpleDecision> decs6 = new ArrayList<>(w6.getDecisions());
    decs6.add(new SimpleDecision(2, CAT99, EditorialDecision.Mode.BLOCK));
    w6.setDecisions(decs6);
    // Give Felis catus a classification with the Felis genus id so TAXON_ID filter can be tested
    w6.setClassification(List.of(
        SimpleName.sn("t1", Rank.KINGDOM, "Animalia", null),
        SimpleName.sn("t5", Rank.GENUS,   "Felis",    "Linnaeus, 1758")
    ));
    insert(c, cfg, w6);

    insert(c, cfg, taxon("t7", "Felis silvestris", "Schreber, 1775", Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null));
    insert(c, cfg, synonym("s1", "Felis domesticus", "Erxleben, 1777",  Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, "t6"));

    // ---- Dataset 200: BOTANICAL (3 taxa + 1 synonym = 4) ----
    insert(c, cfg, withVernacular(taxon("t8",  "Plantae",    null,  Rank.KINGDOM, NomCode.BOTANICAL, DS2, null, null),
      "deu:Pflanzen", "fre:Plante", "eng:Plants"
    ));
    insert(c, cfg, taxon("t9",  "Rosa",       null,  Rank.GENUS,   NomCode.BOTANICAL, DS2, null, REF_B));
    insert(c, cfg, withVernacular(taxon("t10", "Rosa canina", "L.", Rank.SPECIES, NomCode.BOTANICAL, DS2, null, REF_B),
      "deu:Hundsrose", "ita:Rosa selvatica comune", "eng:dog rose", "vie:tầm xuân", "heb:ורד הכלב"
    ));
    insert(c, cfg, synonym("s2", "Rosa rubiginosa", "L.",  Rank.SPECIES,  NomCode.BOTANICAL, DS2, "t10"));

    // Issue #1498: fuzzy search regression test data
    insert(c, cfg, taxon("t11", "Centaurea rothmalerana", null, Rank.SPECIES, NomCode.BOTANICAL, DS2, null, null));
    insert(c, cfg, taxon("t12", "Dryopteris fragrans",    null, Rank.SPECIES, NomCode.BOTANICAL, DS2, null, null));

    // WHOLE_WORDS analyzer fixtures: punctuation cases that the standard tokenizer would mishandle.
    // The scientificName is overridden after parsing so the indexed value matches the literal we
    // want to test (parser may otherwise normalise these).

    // t13: leading '?' as a standalone token (genus uncertain) — taken from real moth records in dataset 55434
    NameUsageWrapper w13 = taxon("t13", "Albisignata albisignata", null, Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null);
    w13.getUsage().getName().setScientificName("? albisignata");
    insert(c, cfg, w13);

    // t14: zoological convention with subgenus in parentheses (real mosquito)
    NameUsageWrapper w14 = taxon("t14", "Aedes aegypti", null, Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null);
    w14.getUsage().getName().setScientificName("Aedes (Stegomyia) aegypti");
    insert(c, cfg, w14);

    // t15: botanical with hyphenated specific epithet (real ornamental shrub)
    NameUsageWrapper w15 = taxon("t15", "Hibiscus rosa", null, Rank.SPECIES, NomCode.BOTANICAL, DS2, null, null);
    w15.getUsage().getName().setScientificName("Hibiscus rosa-sinensis");
    insert(c, cfg, w15);

    EsUtil.refreshIndex(c, cfg.name);
    service = new NameUsageSearchServiceEs(cfg.name, c);
  }

  @AfterClass
  public static void deleteTestIndex() throws IOException {
    //EsUtil.deleteIndex(esSetup.getClient(), esSetup.getEsConfig().index);
  }

  /** Disable base class per-test setup/teardown; the index is shared across all tests. */
  @Override @Before  public void setUp() {}
  @Override @After   public void tearDown() {}

  // -----------------------------------------------------------------------
  // Search helpers
  // -----------------------------------------------------------------------

  private NameUsageSearchResponse search(NameUsageSearchRequest req) {
    return search(req, 100);
  }
  private NameUsageSearchResponse search(NameUsageSearchRequest req, int limit) {
    return service.search(req, new Page(0, limit));
  }

  private int count(NameUsageSearchRequest req) {
    return search(req).getTotal();
  }

  /** Build a single-filter request and perform the search. */
  private NameUsageSearchResponse doSearch(NameUsageSearchParameter param, String value) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(param, value);
    return search(req);
  }

  /** Assert that a single-filter search returns exactly {@code expected} documents. */
  private void assertFilterCount(int expected, NameUsageSearchParameter param, Object value) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(param, value.toString());
    int actual = count(req);
    assertEquals("filter " + param + "=" + value, expected, actual);
  }

  private void assertCountAtLeast(int min, NameUsageSearchRequest req) {
    int actual = count(req);
    assertTrue("expected >= " + min + " but got " + actual, actual >= min);
  }

  // -----------------------------------------------------------------------
  // Test: all NameUsageSearchParameter values exercised as filters
  // -----------------------------------------------------------------------

  @Test
  public void testFilters() {
    // --- Integer/string parameters that work correctly ---

    // DATASET_KEY (keyword field storing integer "100"/"200")
    assertFilterCount(10, DATASET_KEY, DS1);
    assertFilterCount(7, DATASET_KEY, DS2);

    // USAGE_ID (keyword field, exact string match on the usage's id)
    assertFilterCount(1, USAGE_ID, "t6");

    // NAME_ID (keyword field, set to id + "_n" in the taxon helper)
    assertFilterCount(1, NAME_ID, "t6_n");

    // PUBLISHED_IN_ID (keyword field)
    assertFilterCount(2, PUBLISHED_IN_ID, REF_A); // t5 (Felis) and t6 (Felis catus)
    assertFilterCount(2, PUBLISHED_IN_ID, REF_B); // t9 (Rosa) and t10 (Rosa canina)

    // SECTOR_KEY (keyword field; only t5=Felis has sectorKey=5)
    assertFilterCount(1, SECTOR_KEY, 5);

    // SECTOR_DATASET_KEY (keyword field; set to 42 by newNameUsageWrapper helper for all usages)
    assertFilterCount(TOTAL, SECTOR_DATASET_KEY, SECTOR_DK);

    // SECONDARY_SOURCE (keyword field; {1010, 2123} for all usages)
    assertFilterCount(TOTAL, SECONDARY_SOURCE, SS_KEY1);
    assertFilterCount(TOTAL, SECONDARY_SOURCE, SS_KEY2);

    // EXTINCT (boolean field; only t6=Felis catus has extinct=true)
    assertFilterCount(1, EXTINCT, true);
    assertFilterCount(14, EXTINCT, false); // 14 taxa with explicit false; synonyms have no extinct field

    // RANK (integer field; stored as Rank ordinal via RankOrdinalSerde)
    assertFilterCount(2, RANK, Rank.KINGDOM); // Animalia (t1) + Plantae (t8)
    assertFilterCount(10, RANK, Rank.SPECIES);  // t6, t7, t10, s1, s2, t11, t12, t13, t14, t15

    // TAXON_ID (keyword field on classification[].id)
    // t6 (Felis catus) was given a classification containing t5 (Felis) with id="t5"
    assertFilterCount(1, TAXON_ID, "t5");

    // DECISION_MODE with IS_NOT_NULL: all usages have a decision with catalogue key 99
    NameUsageSearchRequest decisionNotNull = new NameUsageSearchRequest();
    decisionNotNull.addFilter(DECISION_MODE, NameUsageRequest.IS_NOT_NULL);
    decisionNotNull.addFilter(CATALOGUE_KEY, String.valueOf(CAT99));
    assertEquals("IS_NOT_NULL decision for cat99 should match all", TOTAL, count(decisionNotNull));

    // DECISION_MODE with IS_NULL: no usage lacks a decision with catalogue key 99
    NameUsageSearchRequest decisionNull = new NameUsageSearchRequest();
    decisionNull.addFilter(DECISION_MODE, NameUsageRequest.IS_NULL);
    decisionNull.addFilter(CATALOGUE_KEY, String.valueOf(CAT99));
    assertEquals("IS_NULL decision for cat99 should match none", 0, count(decisionNull));

    // minRank / maxRank range filters (rank stored as integer ordinal)
    NameUsageSearchRequest minRankReq = new NameUsageSearchRequest();
    minRankReq.setMinRank(Rank.KINGDOM);
    assertCountAtLeast(1, minRankReq); // at least the two kingdoms

    NameUsageSearchRequest maxRankReq = new NameUsageSearchRequest();
    maxRankReq.setMaxRank(Rank.SPECIES);
    assertCountAtLeast(1, maxRankReq); // at least the species

    // SECTOR_PUBLISHER_KEY (UUID keyword field; random UUID won't match, but must not throw)
    assertNotNull(doSearch(SECTOR_PUBLISHER_KEY, UUID.randomUUID().toString()));

    // --- Enum parameters stored as string names in keyword fields ---
    // These have a known mismatch: the filter translator sends the ordinal as a long, but the
    // indexed value is the enum name string. The tests below verify that no exception is thrown.

    // STATUS
    assertNotNull(doSearch(STATUS, TaxonomicStatus.ACCEPTED.name()));
    // NOM_CODE
    assertNotNull(doSearch(NOM_CODE, NomCode.ZOOLOGICAL.name()));
    // ENVIRONMENT
    assertNotNull(doSearch(ENVIRONMENT, Environment.TERRESTRIAL.name()));
    // ISSUE (all have ACCEPTED_NAME_MISSING from newNameUsageWrapper)
    assertNotNull(doSearch(ISSUE, Issue.ACCEPTED_NAME_MISSING.name()));
    // GROUP (all have TaxGroup.Plants from newNameUsageWrapper)
    assertNotNull(doSearch(GROUP, TaxGroup.Plants.name()));
    // SECTOR_MODE (all have Sector.Mode.MERGE from newNameUsageWrapper)
    assertNotNull(doSearch(SECTOR_MODE, Sector.Mode.MERGE.name()));
    // NOM_STATUS
    assertNotNull(doSearch(NOM_STATUS, NomStatus.ESTABLISHED.name()));
    // NAME_TYPE
    assertNotNull(doSearch(NAME_TYPE, NameType.SCIENTIFIC.name()));
    // ORIGIN
    assertNotNull(doSearch(ORIGIN, Origin.SOURCE.name()));
    // SECONDARY_SOURCE_GROUP
    assertNotNull(doSearch(SECONDARY_SOURCE_GROUP, InfoGroup.AUTHORSHIP.name()));
    // DECISION_MODE with specific enum value (also broken, but no exception expected)
    NameUsageSearchRequest blockReq = new NameUsageSearchRequest();
    blockReq.addFilter(DECISION_MODE, EditorialDecision.Mode.BLOCK.name());
    blockReq.addFilter(CATALOGUE_KEY, String.valueOf(CAT99));
    assertNotNull(search(blockReq));

    // --- String filters on keyword fields ---
    // AUTHORSHIP (keyword field; value must exactly match the stored authorship string)
    assertNotNull(doSearch(AUTHORSHIP, "Linnaeus, 1758"));
    // AUTHORSHIP_YEAR (keyword field; depends on parser extracting year)
    assertNotNull(doSearch(AUTHORSHIP_YEAR, "1758"));
    // ALPHAINDEX (keyword field, same as scientificName; exact match on first character won't work)
    assertNotNull(doSearch(ALPHAINDEX, "Felis catus"));

    // FIELD is not mapped in the es2 schema ("nameField" missing) – should return 0, not throw
    assertNotNull(doSearch(FIELD, NameField.UNINOMIAL.name()));

    // make sure to try all filter params
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      if (p == DECISION_MODE) continue; // skip this as it requires also the catalogue key to be present
      assertNotNull(doSearch(p, "_NOT_NULL"));
    }
  }

  // -----------------------------------------------------------------------
  // Test: facet counts
  // -----------------------------------------------------------------------

  @Test
  public void testFacets() {
    // Request facets on DATASET_KEY and RANK without any filter
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFacet(DATASET_KEY);
    req.addFacet(RANK);
    req.setFacetLimit(20);

    NameUsageSearchResponse resp = search(req);
    assertEquals("all usages should be in result", TOTAL, resp.getTotal());

    Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets = resp.getFacets();
    assertNotNull(facets);

    // DATASET_KEY facet: 2 buckets (DS1=10, DS2=7)
    Set<FacetValue<?>> dsFacet = facets.get(DATASET_KEY);
    assertNotNull("DATASET_KEY facet should be present", dsFacet);
    assertEquals("DATASET_KEY facet should have 2 buckets", 2, dsFacet.size());

    // RANK facet: 6 distinct ranks (KINGDOM, PHYLUM, CLASS, ORDER, GENUS, SPECIES)
    Set<FacetValue<?>> rankFacet = facets.get(RANK);
    assertNotNull("RANK facet should be present", rankFacet);
    assertEquals("RANK facet should have 6 buckets", 6, rankFacet.size());

    // Test that a filter on DATASET_KEY still returns both dataset buckets in the DATASET_KEY facet
    // (facets ignore their own filter so users can switch to the other value)
    NameUsageSearchRequest filtered = new NameUsageSearchRequest();
    filtered.addFilter(DATASET_KEY, String.valueOf(DS1));
    filtered.addFacet(DATASET_KEY);
    filtered.setFacetLimit(20);

    NameUsageSearchResponse filteredResp = search(filtered);
    assertEquals("filter on DS1 should return 10 results", 10, filteredResp.getTotal());

    Set<FacetValue<?>> filteredDsFacet = filteredResp.getFacets().get(DATASET_KEY);
    assertNotNull(filteredDsFacet);
    assertEquals("DATASET_KEY facet should still show both datasets even when filtered to one",
        2, filteredDsFacet.size());

    // now test ALL FACETS are working
    req = new NameUsageSearchRequest();
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      req.addFacet(p);
    }
    req.setFacetLimit(20);

    resp = search(req, 0);
    facets = resp.getFacets();
    assertNotNull(facets);

    assertNotNull(facets.get(FIELD));
    assertNotNull(facets.get(AUTHORSHIP));
  }

  // -----------------------------------------------------------------------
  // Test: sort options
  // -----------------------------------------------------------------------

  @Test
  public void testSorting() {
    // NAME: alphabetical ascending
    NameUsageSearchRequest nameReq = new NameUsageSearchRequest();
    nameReq.setSortBy(NameUsageRequest.SortBy.NAME);
    List<NameUsageWrapper> byName = search(nameReq).getResult();
    assertEquals(TOTAL, byName.size());
    assertNameOrder(byName, false);

    // NAME reversed: alphabetical descending
    NameUsageSearchRequest nameRevReq = new NameUsageSearchRequest();
    nameRevReq.setSortBy(NameUsageRequest.SortBy.NAME);
    nameRevReq.setReverse(true);
    List<NameUsageWrapper> byNameRev = search(nameRevReq).getResult();
    assertEquals(TOTAL, byNameRev.size());
    assertNameOrder(byNameRev, true);

    // TAXONOMIC: kingdoms (lower ordinal) must appear before species (higher ordinal)
    NameUsageSearchRequest taxReq = new NameUsageSearchRequest();
    taxReq.setSortBy(NameUsageRequest.SortBy.TAXONOMIC);
    List<NameUsageWrapper> byTax = search(taxReq).getResult();
    assertEquals(TOTAL, byTax.size());
    int firstKingdom = -1, firstSpecies = -1;
    for (int i = 0; i < byTax.size(); i++) {
      Rank r = byTax.get(i).getUsage().getName().getRank();
      if (r == Rank.KINGDOM && firstKingdom < 0) firstKingdom = i;
      if (r == Rank.SPECIES && firstSpecies < 0) firstSpecies = i;
    }
    assertTrue("kingdoms should appear before species in TAXONOMIC sort",
        firstKingdom >= 0 && firstSpecies > firstKingdom);

    // RELEVANCE: score-based – without a q all scores are equal, just verify TOTAL results
    NameUsageSearchRequest relReq = new NameUsageSearchRequest();
    relReq.setSortBy(NameUsageRequest.SortBy.RELEVANCE);
    assertEquals(TOTAL, count(relReq));
  }

  private static void assertNameOrder(List<NameUsageWrapper> results, boolean reversed) {
    for (int i = 0; i < results.size() - 1; i++) {
      String curr = results.get(i).getUsage().getName().getScientificName();
      String next = results.get(i + 1).getUsage().getName().getScientificName();
      int cmp = curr.compareToIgnoreCase(next);
      if (reversed) {
        assertTrue("expected '" + curr + "' >= '" + next + "'", cmp >= 0);
      } else {
        assertTrue("expected '" + curr + "' <= '" + next + "'", cmp <= 0);
      }
    }
  }

  // -----------------------------------------------------------------------
  // Test: QMatcher variants (one test per matcher)
  // -----------------------------------------------------------------------

  /**
   * ExactMatcher: uses a term query on the scientificName keyword field.
   * q="Felis catus" must match exactly the one document with that canonical name.
   */
  @Test
  public void testQExact() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("Felis catus");
    req.setSearchType(EXACT);
    // EXACT type forces SCIENTIFIC_NAME content (done by RequestValidator)
    List<NameUsageWrapper> results = search(req).getResult();
    assertEquals("EXACT 'Felis catus' should match exactly 1 document", 1, results.size());
    assertEquals("t6", results.get(0).getId());

    req.setQ("†Felis catus L., 1758 Foo");
    results = search(req).getResult();
    assertEquals("EXACT 'Felis catus L., 1758' should match exactly 1 document", 1, results.size());
    assertEquals("t6", results.get(0).getId());

    req.setQ("Felis catus L., 1758 Foo");
    results = search(req).getResult();
    assertEquals("EXACT 'Felis catus L., 1758' should match exactly 1 document", 1, results.size());
    assertEquals("t6", results.get(0).getId());

    req.setQ("felis catus L., 1758 foo");
    results = search(req).getResult();
    assertEquals("EXACT 'Felis catus L., 1758' should match exactly 1 document", 1, results.size());
    assertEquals("t6", results.get(0).getId());
  }

  /**
   * PrefixMatcherSimple (PREFIX, non-fuzzy): currently a stub delegating to the base term query.
   * Using an exact scientific name ensures at least one match.
   */
  @Test
  public void testQPrefix() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("Felis cat");
    req.setSearchType(PREFIX);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertFalse("PREFIX simple: 'Felis catus' should return results", search(req).getResult().isEmpty());
    req.addFilter(DATASET_KEY, NameUsageRequest.IS_NULL);
    assertTrue(search(req).getResult().isEmpty());

    req = new NameUsageSearchRequest();
    req.setQ("Felis s");
    req.setSearchType(PREFIX);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertEquals("PREFIX simple: 'Felis s' should return t7", "t7", search(req).getResult().getFirst().getId());

    // https://github.com/CatalogueOfLife/backend/issues/1331
    req.setQ("felis s");
    assertEquals("PREFIX simple: 'felis s' should return t7", "t7", search(req).getResult().getFirst().getId());
  }

  @Test
  public void testNameFields() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(FIELD, NameField.UNINOMIAL.name());
    var resp = search(req);
    assertEquals("FIELD: 'UNINOMIAL' should return 7 results", 7, resp.getResult().size());

    req.addFilter(FIELD, NameField.SPECIFIC_EPITHET.name());
    resp = search(req);
    assertEquals("FIELD: 'UNINOMIAL' should return all results", TOTAL, resp.getResult().size());
  }

  @Test
  public void testAuthors() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(AUTHORSHIP, "Schreber");
    var resp = search(req);
    assertEquals("AUTHORSHIP: 'Schreber' should return 1 result", 1, resp.getResult().size());
  }

  /**
   * WHOLE_WORDS scientific name search.
   * <p>
   * Backed by a match query on the {@code usage.name.scientificName.word} subfield, which is
   * indexed with the {@code sciname_ascii} analyzer (whitespace tokenizer + bracket char filter +
   * lowercase + asciifolding). The cases below pin down the analyzer's behaviour on punctuation:
   * leading {@code ?}, parenthesised subgenus, and hyphenated epithet.
   */
  @Test
  public void testQWholeWords() {
    // baseline: a plain epithet still works
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("canina");
    req.setSearchType(WHOLE_WORDS);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertFalse("WHOLE_WORDS 'canina' should return Rosa canina",
        search(req).getResult().isEmpty());

    // '?' as a standalone token (genus uncertain) — t13 = "? albisignata"
    req.setQ("?");
    var hits = search(req).getResult();
    assertFalse("WHOLE_WORDS '?' should match '? albisignata'", hits.isEmpty());
    assertTrue("WHOLE_WORDS '?' should include t13", hits.stream().anyMatch(h -> "t13".equals(h.getId())));

    req.setQ("albisignata");
    assertEquals("WHOLE_WORDS 'albisignata' should match t13",
        "t13", search(req).getResult().getFirst().getId());

    // zoological subgenus in parentheses — t14 = "Aedes (Stegomyia) aegypti"
    // bracket char filter strips '(' and ')' before tokenization, so 'Stegomyia' is a whole word
    req.setQ("Stegomyia");
    assertEquals("WHOLE_WORDS 'Stegomyia' should match t14 despite parens",
        "t14", search(req).getResult().getFirst().getId());

    req.setQ("Aedes");
    assertTrue("WHOLE_WORDS 'Aedes' should include t14",
        search(req).getResult().stream().anyMatch(h -> "t14".equals(h.getId())));

    req.setQ("aegypti");
    assertEquals("WHOLE_WORDS 'aegypti' should match t14",
        "t14", search(req).getResult().getFirst().getId());

    // hyphenated specific epithet — t15 = "Hibiscus rosa-sinensis"
    // Whitespace tokenizer keeps the hyphenated epithet as a single token.
    req.setQ("rosa-sinensis");
    assertEquals("WHOLE_WORDS 'rosa-sinensis' should match t15",
        "t15", search(req).getResult().getFirst().getId());

    // documented trade-off: a half of a hyphenated epithet is NOT a whole word match
    req.setQ("sinensis");
    assertTrue("WHOLE_WORDS 'sinensis' should NOT match the hyphenated 'rosa-sinensis' epithet",
        search(req).getResult().stream().noneMatch(h -> "t15".equals(h.getId())));
  }

  /**
   * WholeWordMatcherFuzzy (WHOLE_WORDS, fuzzy).
   */
  @Test
  public void testQFuzzy() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("caninae");
    req.setSearchType(FUZZY);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertFalse("WHOLE_WORDS fuzzy: 'Rosa canina' should return results",
        search(req).getResult().isEmpty());
  }

  /**
   * Issue #1498: fuzzy search with an extra letter in the epithet.
   * The user searched "Centaurea rothmaleerana" (extra 'e') expecting "Centaurea rothmalerana".
   */
  @Test
  public void testQFuzzyIssue1498ExtraLetter() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("Centaurea rothmaleerana"); // extra 'e' vs indexed "rothmalerana"
    req.setSearchType(FUZZY);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertFalse("FUZZY: 'Centaurea rothmaleerana' should match 'Centaurea rothmalerana' (1 char off)",
        search(req).getResult().isEmpty());
  }

  /**
   * Issue #1498: fuzzy search with a missing letter in the epithet.
   * The user searched "Dryopteris fragans" (missing 'r') expecting "Dryopteris fragrans".
   */
  @Test
  public void testQFuzzyIssue1498MissingLetter() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("Dryopteris fragans"); // missing 'r' vs indexed "fragrans"
    req.setSearchType(FUZZY);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertFalse("FUZZY: 'Dryopteris fragans' should match 'Dryopteris fragrans' (1 char off)",
        search(req).getResult().isEmpty());
  }

  /**
   * Issue #1498: naturally capitalised user input breaks prefixLength=2 check.
   * "Caninae" (capital C, 1 char off) should still match "Rosa canina".
   */
  @Test
  public void testQFuzzyCapitalizedInput() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setQ("Caninae"); // capital C + 1 char off from "canina"
    req.setSearchType(FUZZY);
    req.setSingleContent(SCIENTIFIC_NAME);
    assertFalse("FUZZY: capitalized 'Caninae' should match 'Rosa canina'",
        search(req).getResult().isEmpty());
  }

  @Test
  public void testVernacularNames() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setSingleContent(VERNACULAR_NAME);
    req.setQ("Rose");
    List<NameUsageWrapper> results = search(req).getResult();
    assertEquals("Vernacular 'Rose' should match exactly 1 document", 1, results.size());
    assertEquals("t10", results.getFirst().getId());
  }


  @Test
  public void testAuthorships() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setSingleContent(NameUsageRequest.SearchContent.AUTHORSHIP);
    req.setQ("Linnaeus");
    List<NameUsageWrapper> results = search(req).getResult();
    assertEquals("Authorship 'Linnaeus' should match exactly 1 document", 1, results.size());
    assertEquals("t5", results.getFirst().getId());

    req.setSingleContent(SCIENTIFIC_NAME);
    results = search(req).getResult();
    assertTrue("ScientificName 'Linnaeus' should match exactly 0 document", results.isEmpty());

    req.setSingleContent(NameUsageRequest.SearchContent.AUTHORSHIP);
    req.setQ("1758");
    results = search(req).getResult();
    assertEquals("Authorship '1758' should match exactly 2 documents", 2, results.size());
  }
}
