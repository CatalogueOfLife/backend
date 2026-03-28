package life.catalogue.es.suggest;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.EsTestBase;
import life.catalogue.es.EsUtil;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.*;

import static life.catalogue.api.TestEntityGenerator.*;
import static life.catalogue.es.TestIndexUtils.*;
import static org.junit.Assert.*;

public class NameUsageSuggestionServiceEsIT extends EsTestBase {
  static final int DS1 = 100;

  private int idx;
  private NameUsageSuggestionServiceEs service;

  @Before
  @Override
  public void setUp() throws IOException {
    super.setUp();
    idx = 100;
    service = new NameUsageSuggestionServiceEs(indexName, client);
  }

  @After
  @Override
  public void tearDown() throws IOException {
    super.tearDown();
  }

  @Test
  public void suggest() throws Exception {
    setupSuggestTestData();

    var req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setQ("feli");
    var resp = service.suggest(req);
    assertEquals(8, resp.size());

    req.setQ("cat");
    resp = service.suggest(req);
    assertEquals(1, resp.size());

    req.setQ("dom");
    req.setAccepted(true);
    resp = service.suggest(req);
    assertEquals(0, resp.size());

    req.setQ("feli");
    req.setMaxRank(Rank.GENUS);
    resp = service.suggest(req);
    assertEquals(6, resp.size());
    assertEquals("t5", resp.getFirst().getUsageId());
    assertEquals("t6", resp.get(1).getUsageId());
    assertEquals("t7", resp.get(2).getUsageId());

    req.setMinRank(Rank.SPECIES);
    resp = service.suggest(req);
    assertEquals(3, resp.size());

    req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setQ("silv");
    req.setSortBy(NameUsageRequest.SortBy.TAXONOMIC);
    resp = service.suggest(req);
    assertEquals(4, resp.size());
    assertEquals("t7", resp.getFirst().getUsageId());
    assertEquals("t10", resp.get(1).getUsageId()); // sorted by rank, then name

    req.setSortBy(NameUsageRequest.SortBy.RELEVANCE);
    resp = service.suggest(req);
    assertEquals(4, resp.size());
    assertEquals("t7", resp.getFirst().getUsageId());
    assertEquals("t8", resp.get(1).getUsageId());

    req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);

    req.setQ("Puma concolor");
    resp = service.suggest(req);
    // expect Puma concolor as first suggestion
    assertEquals("t100", resp.getFirst().getUsageId());

    req.setQ("Puma con");
    resp = service.suggest(req);
    // expect Puma concolor as first suggestion
    assertEquals("t100", resp.getFirst().getUsageId());

    req.setQ("Puma co");
    resp = service.suggest(req);
    // expect Puma concolor as first suggestion
    assertEquals("t100", resp.getFirst().getUsageId());

    req.setQ("Puma concol");
    resp = service.suggest(req);
    // expect Puma concolor as first suggestion
    assertEquals("t100", resp.getFirst().getUsageId());

    // Rank-based scoring in RELEVANCE sort
    // Single-word query: higher ranks (family, genus) score above species/subspecies
    req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setSortBy(NameUsageRequest.SortBy.RELEVANCE);

    req.setQ("Phoc");
    resp = service.suggest(req);
    assertEquals("t113", resp.getFirst().getUsageId()); // Phocidae (FAMILY) – highest rank
    assertEquals("t114", resp.get(1).getUsageId());     // Phoca (GENUS)
    assertEquals("t115", resp.get(2).getUsageId());     // Phoca vitulina (SPECIES)

    // make sure we're case insensitive
    req.setQ("PHoC");
    resp = service.suggest(req);
    assertEquals("t113", resp.getFirst().getUsageId()); // Phocidae (FAMILY) – highest rank
    assertEquals("t114", resp.get(1).getUsageId());     // Phoca (GENUS)
    assertEquals("t115", resp.get(2).getUsageId());     // Phoca vitulina (SPECIES)

    // Multi-word prefix: species ranked above subspecies
    req.setQ("Phoca vit");
    resp = service.suggest(req);
    assertEquals("t115", resp.getFirst().getUsageId()); // Phoca vitulina (SPECIES)
    assertEquals("t116", resp.get(1).getUsageId());     // Phoca vitulina vitulina (SUBSPECIES)

    req.setQ("Phoca");
    resp = service.suggest(req);
    assertEquals("t114", resp.getFirst().getUsageId()); // exact match against Phoca (GENUS)
    assertEquals("t115", resp.get(1).getUsageId());     // Phoca vitulina (SPECIES)

    req.setQ("Phoca");
    resp = service.suggest(req);
    assertEquals("t114", resp.getFirst().getUsageId()); // exact match against Phoca (GENUS)
    assertEquals("t115", resp.get(1).getUsageId());     // Phoca vitulina (SPECIES)
  }

  /**
   * Tests suggestions loaded from the Taraxacum JSON resource (suggest API response format).
   * Pays special attention to phrase prefix queries ending with a single letter after a space,
   * e.g. "Taraxacum a", which must only match names whose second word starts with that letter.
   */
  @Test
  public void suggestTaraxacum() throws Exception {
    indexFromSuggestResources("taraxacum.json");
    EsUtil.refreshIndex(client, indexName);

    var req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setSortBy(NameUsageRequest.SortBy.RELEVANCE);

    // Single-word query: genus ranks above species
    req.setQ("Taraxacum");
    var resp = service.suggest(req);
    assertFalse("Taraxacum should return results", resp.isEmpty());
    assertEquals("taraxacum-genus", resp.getFirst().getUsageId());

    // Single letter after space: only names whose second word starts with 'a'
    req.setQ("Taraxacum a");
    resp = service.suggest(req);
    assertFalse("'Taraxacum a' should return species starting with a", resp.isEmpty());
    resp.forEach(s -> assertTrue(
        "match '" + s.getMatch() + "' should start with 'Taraxacum a' (case-insensitive)",
        s.getMatch().toLowerCase().startsWith("taraxacum a")
    ));
    // officinale and palustre must NOT appear
    resp.forEach(s -> assertFalse(
        s.getMatch() + " should not match 'Taraxacum a'",
        s.getMatch().toLowerCase().contains("officinale") || s.getMatch().toLowerCase().contains("palustre")
    ));

    // Longer prefix
    req.setQ("Taraxacum off");
    resp = service.suggest(req);
    assertEquals(1, resp.size());
    assertEquals("taraxacum-officinale", resp.getFirst().getUsageId());

    // Accepted filter suppresses synonyms
    req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setAccepted(true);
    req.setQ("Leontodon");
    resp = service.suggest(req);
    assertEquals(0, resp.size());
  }

  /**
   * Tests suggestions loaded from the Quercus JSON resource (suggest API response format).
   * Verifies rank-based scoring and synonym filtering.
   */
  @Test
  public void suggestQuercus() throws Exception {
    indexFromSuggestResources("quercus.json");
    EsUtil.refreshIndex(client, indexName);

    var req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setSortBy(NameUsageRequest.SortBy.RELEVANCE);

    // Genus ranks above species for single-word query
    req.setQ("Quercus");
    var resp = service.suggest(req);
    assertFalse(resp.isEmpty());
    assertEquals("quercus-genus", resp.getFirst().getUsageId());

    // Multi-word prefix: species before subspecies
    req.setQ("Quercus ro");
    resp = service.suggest(req);
    assertFalse(resp.isEmpty());
    assertEquals("quercus-robur", resp.getFirst().getUsageId());

    // Species before its subspecies
    req.setQ("Quercus robur");
    resp = service.suggest(req);
    assertTrue(resp.size() >= 2);
    assertEquals("quercus-robur", resp.getFirst().getUsageId());
    assertEquals("quercus-robur-robur", resp.get(1).getUsageId());

    // Accepted filter excludes synonym
    req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setAccepted(true);
    req.setQ("Quercus ped");
    resp = service.suggest(req);
    assertEquals(0, resp.size());

    // Without accepted filter, synonym is found
    req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setQ("Quercus ped");
    resp = service.suggest(req);
    assertEquals(1, resp.size());
    assertEquals("quercus-pedunculata", resp.getFirst().getUsageId());
  }

  @Test
  public void suggestPuma() throws Exception {
    indexFromSuggestResources("puma.json", "puma-concolor.json");
    EsUtil.refreshIndex(client, indexName);

    assertFirst("Puma", Rank.GENUS, "75F9", "Puma Jardine, 1834");
    assertFirst("Puma con", Rank.SPECIES, "4QHKG", "Puma concolor (Linnaeus, 1771)");
    assertFirst("Puma concolor", Rank.SPECIES, "4QHKG", "Puma concolor (Linnaeus, 1771)");
  }

  private void assertFirst(String query, Rank rank, String uid, String label) {
    var req = new NameUsageSuggestRequest();
    req.setDatasetFilter(DS1);
    req.setQ(query);
    var resp = service.suggest(req);
    assertEquals(uid, resp.getFirst().getUsageId());
    assertEquals(rank, resp.getFirst().getRank());
    assertEquals(label, resp.getFirst().getMatch());
  }


    // ── Index setup helpers ───────────────────────────────────────────────────────

  /** Populates the per-test index from a JSON file in suggest-API response format. */
  private void indexFromSuggestResources(String... resourcePaths) throws Exception {
    Set<String> inserted = new HashSet<>();
    for (String resourcePath : resourcePaths) {
      try (var is = getClass().getResourceAsStream("/suggest/" + resourcePath)) {
        assertNotNull("resource not found: " + resourcePath, is);
        List<NameUsageSuggestion> suggestions = ApiModule.MAPPER.readValue(
            is, new TypeReference<List<NameUsageSuggestion>>() {});
        for (var sug : suggestions) {
          if (inserted.add(sug.getUsageId())) {
            insert(client, cfg.index, toWrapper(sug));
          }
        }
      }
    }
  }

  /**
   * Converts a {@link NameUsageSuggestion} (suggest-API response format) into a
   * {@link NameUsageWrapper} suitable for indexing into Elasticsearch.
   */
  private NameUsageWrapper toWrapper(NameUsageSuggestion sug) throws Exception {
    var rank = sug.getRank() != null ? sug.getRank() : Rank.UNRANKED;
    var code = sug.getNomCode();
    var status = sug.getStatus() != null ? sug.getStatus() : TaxonomicStatus.ACCEPTED;

    Name n = parseName(sug.getMatch(), null, rank, code);
    n.setDatasetKey(DS1);
    n.setId(sug.getNameId() != null ? sug.getNameId() : sug.getUsageId() + "_n");

    NameUsageWrapper nuw;
    if (status.isSynonym()) {
      Synonym s = newSynonym(status, n, sug.getAcceptedUsageId());
      s.setId(sug.getUsageId());
      nuw = newNameUsageWrapper(s);
    } else {
      Taxon t = newTaxon(n, sug.getUsageId(), null);
      t.setStatus(status);
      nuw = newNameUsageWrapper(t);
    }
    nuw.getUsage().setNamePhrase(null);
    nuw.setGroup(sug.getGroup());

    // Build classification: [family context, (accepted taxon for synonyms,) self]
    List<SimpleName> cls = new ArrayList<>();
    if (sug.getContext() != null) {
      var family = new SimpleName();
      family.setName(sug.getContext());
      family.setRank(Rank.FAMILY);
      cls.add(family);
    }
    if (status.isSynonym() && sug.getAcceptedUsageId() != null) {
      var acc = new SimpleName();
      acc.setId(sug.getAcceptedUsageId());
      if (sug.getAcceptedName() != null) {
        Name accParsed = parseName(sug.getAcceptedName(), null, null, code);
        acc.setName(accParsed.getScientificName());
        acc.setAuthorship(accParsed.getAuthorship());
      }
      cls.add(acc);
    }
    if (nuw.getUsage() instanceof NameUsageBase nub) {
      cls.add(new SimpleName(nub));
    }
    nuw.setClassification(cls);

    return nuw;
  }

  /** Sets up the index with the fixed dataset used by the {@link #suggest()} test. */
  private void setupSuggestTestData() throws Exception {
    LinkedList<SimpleName> classifications = new LinkedList<>();
    insert(client, cfg.index, withClassification(classifications,
      taxon("t1", "Animalia", null,  Rank.KINGDOM, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(client, cfg.index, withClassification(classifications,
      taxon("t2", "Chordata", null,  Rank.PHYLUM,  NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(client, cfg.index, withClassification(classifications,
      taxon("t3", "Mammalia", null,  Rank.CLASS,   NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(client, cfg.index, withClassification(classifications,
      taxon("t4", "Felidae", null, Rank.FAMILY,   NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(client, cfg.index, withClassification(classifications,
      taxon("t5", "Felis", "Linnaeus, 1758", Rank.GENUS,   NomCode.ZOOLOGICAL, DS1,    5, null)
    ));
    insert(client, cfg.index, withClassification(classifications,
      withExtinct(taxon("t6", "Felis catus", "L., 1758", Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null), true)
    ));
    insert(client, cfg.index, withClassification(classifications,
      synonym("s1", "Felis domesticus", "Erxleben, 1777",  Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, "t6")
    ));
    classifications.removeLast(); // syn
    classifications.removeLast(); // Felis catus
    insert(client, cfg.index, withClassification(classifications,
      taxon("t7", "Felis silvestris", "Schreber, 1775", Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(client, cfg.index, withClassification(classifications,
      taxon("t8", "Felis silvestris silvestris", "Schreber, 1775", Rank.SUBSPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    classifications.removeLast(); // Felis catus
    insert(client, cfg.index, withClassification(classifications,
      taxon("t9", "Felis silvestris vulgaris", "Döring, 1993", Rank.SUBSPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    classifications.removeLast(); // Felis catus
    insert(client, cfg.index, withClassification(classifications,
      taxon("t10", "Felis silvestris anatolica", "Greg, 1887", Rank.SUBSPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));

    insert(client, cfg.index, tax(Rank.SPECIES, "Puma concolor", "(Linnaeus, 1771)"));
    insert(client, cfg.index, tax(Rank.SUBSPECIES, "Puma concolor concolor", "(Linnaeus, 1771)"));
    insert(client, cfg.index, syn(Rank.SUBSPECIES, "Puma concolor puma", "(Molina, 1782)"));
    insert(client, cfg.index, tax(Rank.SUBSPECIES, "Puma concolor couguar", "(Kerr, 1792)"));
    insert(client, cfg.index, tax(Rank.SPECIES, "Sanogasta puma", "Ramírez, 2003"));
    insert(client, cfg.index, tax(Rank.SPECIES, "Pimelodus puma", "Girard, 1859"));
    insert(client, cfg.index, tax(Rank.VARIETY, "Candelaria concolor var. concolor"));
    insert(client, cfg.index, syn(Rank.SPECIES, "Loasa puma-chini", "Weigend"));
    insert(client, cfg.index, tax(Rank.SUBSPECIES, "Ptyonoprogne concolor concolor", "(Sykes, 1832)"));
    insert(client, cfg.index, tax(Rank.SPECIES, "Anolis concolor", "Cope, 1863"));
    insert(client, cfg.index, syn(Rank.VARIETY, "Cypraea (Luria) lurida var. concolor", "Kobelt, 1906"));
    insert(client, cfg.index, tax(Rank.VARIETY, "Camponotus rubripes var. concolor"));
    insert(client, cfg.index, mis(Rank.SPECIES, "Mytilaspis concolor", "auct. non Essig & Baker, 1909"));

    // Phocidae – used to verify rank-based scoring in RELEVANCE sort (t113–t117)
    insert(client, cfg.index, tax(Rank.FAMILY,     "Phocidae",                   "Gray, 1821"));
    insert(client, cfg.index, tax(Rank.GENUS,      "Phoca",                      "Linnaeus, 1758"));
    insert(client, cfg.index, tax(Rank.SPECIES,    "Phoca vitulina",              "Linnaeus, 1758"));
    insert(client, cfg.index, tax(Rank.SUBSPECIES, "Phoca vitulina vitulina",     "Linnaeus, 1758"));
    insert(client, cfg.index, tax(Rank.SUBSPECIES, "Phoca vitulina mellonae",     "Doutt, 1942"));

    EsUtil.refreshIndex(client, indexName);
  }

  private NameUsageWrapper tax(Rank rank, String name) throws Exception {
    return tax(rank, name, null);
  }

  private NameUsageWrapper tax(Rank rank, String name, String author) throws Exception {
    return any(TaxonomicStatus.ACCEPTED, rank, name, author);
  }

  private NameUsageWrapper syn(Rank rank, String name, String author) throws Exception {
    return any(TaxonomicStatus.SYNONYM, rank, name, author);
  }

  private NameUsageWrapper mis(Rank rank, String name, String author) throws Exception {
    return any(TaxonomicStatus.MISAPPLIED, rank, name, author);
  }

  private NameUsageWrapper any(TaxonomicStatus status, Rank rank, String name, String author) throws Exception {
    NameUsageWrapper nuw;
    if (status.isSynonym()) {
      nuw = synonym("s" + idx++, name, author, rank, NomCode.ZOOLOGICAL, DS1, "t7");
    } else {
      nuw = taxon("t" + idx++, name, author, rank, NomCode.ZOOLOGICAL, DS1, null, null);
    }
    nuw.getUsage().setNamePhrase(null);
    return nuw;
  }
}
