package life.catalogue.es.suggest;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.IndexConfig;
import life.catalogue.es.EsTestBase;
import life.catalogue.es.EsUtil;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.LinkedList;

import org.junit.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import static life.catalogue.es.TestIndexUtils.*;
import static org.junit.Assert.assertEquals;

public class NameUsageSuggestionServiceEsIT extends EsTestBase {
  static final int DS1 = 100;
  static int idx = 100;

  private static NameUsageSuggestionServiceEs service;

  public NameUsageSuggestionServiceEsIT() {
    super();
  }

  @BeforeClass
  public static void indexTestData() throws Exception {
    ElasticsearchClient c = esSetup.getClient();
    IndexConfig cfg = esSetup.getEsConfig().index;
    EsUtil.createIndex(c, cfg);

    LinkedList<SimpleName> classifications = new LinkedList<>();
    insert(c, cfg, withClassification(classifications,
      taxon("t1", "Animalia", null,  Rank.KINGDOM, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(c, cfg, withClassification(classifications,
      taxon("t2", "Chordata", null,  Rank.PHYLUM,  NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(c, cfg, withClassification(classifications,
      taxon("t3", "Mammalia", null,  Rank.CLASS,   NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(c, cfg, withClassification(classifications,
      taxon("t4", "Felidae", null, Rank.FAMILY,   NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(c, cfg, withClassification(classifications,
      taxon("t5", "Felis", "Linnaeus, 1758", Rank.GENUS,   NomCode.ZOOLOGICAL, DS1,    5, null)
    ));
    insert(c, cfg, withClassification(classifications,
      withExtinct(taxon("t6", "Felis catus", "L., 1758", Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null), true)
    ));
    insert(c, cfg, withClassification(classifications,
      synonym("s1", "Felis domesticus", "Erxleben, 1777",  Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, "t6")
    ));
    classifications.removeLast(); // syn
    classifications.removeLast(); // Felis catus
    insert(c, cfg, withClassification(classifications,
      taxon("t7", "Felis silvestris", "Schreber, 1775", Rank.SPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    insert(c, cfg, withClassification(classifications,
      taxon("t8", "Felis silvestris silvestris", "Schreber, 1775", Rank.SUBSPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    classifications.removeLast(); // Felis catus
    insert(c, cfg, withClassification(classifications,
      taxon("t9", "Felis silvestris vulgaris", "Döring, 1993", Rank.SUBSPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));
    classifications.removeLast(); // Felis catus
    insert(c, cfg, withClassification(classifications,
      taxon("t10", "Felis silvestris anatolica", "Greg, 1887", Rank.SUBSPECIES, NomCode.ZOOLOGICAL, DS1, null, null)
    ));


    insert(c, cfg, tax(Rank.SPECIES, "Puma concolor", "(Linnaeus, 1771)"));
    insert(c, cfg, tax(Rank.SUBSPECIES, "Puma concolor concolor", "(Linnaeus, 1771)"));
    insert(c, cfg, syn(Rank.SUBSPECIES, "Puma concolor puma", "(Molina, 1782)"));
    insert(c, cfg, tax(Rank.SUBSPECIES, "Puma concolor couguar", "(Kerr, 1792)"));
    insert(c, cfg, tax(Rank.SPECIES, "Sanogasta puma", "Ramírez, 2003"));
    insert(c, cfg, tax(Rank.SPECIES, "Pimelodus puma", "Girard, 1859"));
    insert(c, cfg, tax(Rank.VARIETY, "Candelaria concolor var. concolor"));
    insert(c, cfg, syn(Rank.SPECIES, "Loasa puma-chini", "Weigend"));
    insert(c, cfg, tax(Rank.SUBSPECIES, "Ptyonoprogne concolor concolor", "(Sykes, 1832)"));
    insert(c, cfg, tax(Rank.SPECIES, "Anolis concolor", "Cope, 1863"));
    insert(c, cfg, syn(Rank.VARIETY, "Cypraea (Luria) lurida var. concolor", "Kobelt, 1906"));
    insert(c, cfg, tax(Rank.VARIETY, "Camponotus rubripes var. concolor"));
    insert(c, cfg, mis(Rank.SPECIES, "Mytilaspis concolor", "auct. non Essig & Baker, 1909"));

    // Phocidae – used to verify rank-based scoring in RELEVANCE sort (t113–t117)
    insert(c, cfg, tax(Rank.FAMILY,     "Phocidae",                   "Gray, 1821"));
    insert(c, cfg, tax(Rank.GENUS,      "Phoca",                      "Linnaeus, 1758"));
    insert(c, cfg, tax(Rank.SPECIES,    "Phoca vitulina",              "Linnaeus, 1758"));
    insert(c, cfg, tax(Rank.SUBSPECIES, "Phoca vitulina vitulina",     "Linnaeus, 1758"));
    insert(c, cfg, tax(Rank.SUBSPECIES, "Phoca vitulina mellonae",     "Doutt, 1942"));

    EsUtil.refreshIndex(c, cfg.name);
    service = new NameUsageSuggestionServiceEs(cfg.name, c);
  }

  private static NameUsageWrapper tax(Rank rank, String name) throws Exception {
    return tax(rank, name, null);
  }
  private static NameUsageWrapper tax(Rank rank, String name, String author) throws Exception {
    return any(TaxonomicStatus.ACCEPTED, rank, name, author);
  }
  private static NameUsageWrapper syn(Rank rank, String name, String author) throws Exception {
    return any(TaxonomicStatus.SYNONYM, rank, name, author);
  }
  private static NameUsageWrapper mis(Rank rank, String name, String author) throws Exception {
    return any(TaxonomicStatus.MISAPPLIED, rank, name, author);
  }
  private static NameUsageWrapper any(TaxonomicStatus status, Rank rank, String name, String author) throws Exception {
    NameUsageWrapper nuw;
    if (status.isSynonym()) {
      nuw = synonym("s"+idx++, name, author, rank, NomCode.ZOOLOGICAL, DS1, "t7");
    } else {
      nuw = taxon("t"+idx++, name, author, rank, NomCode.ZOOLOGICAL, DS1, null, null);
    }
    nuw.getUsage().setNamePhrase(null);
    return nuw;
  }

  @AfterClass
  public static void deleteTestIndex() throws IOException {
    EsUtil.deleteIndex(esSetup.getClient(), esSetup.getEsConfig().index.name);
  }

  /** Disable base class per-test setup/teardown; the index is shared across all tests. */
  @Override @Before public void setUp() {}
  @Override @After  public void tearDown() {}

  @Test
  public void suggest() {
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

}