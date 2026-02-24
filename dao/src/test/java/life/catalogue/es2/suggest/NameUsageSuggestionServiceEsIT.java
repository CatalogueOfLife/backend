package life.catalogue.es2.suggest;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.config.IndexConfig;
import life.catalogue.es2.EsTestBase;
import life.catalogue.es2.EsUtil;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.LinkedList;

import org.junit.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import static life.catalogue.es2.TestIndexUtils.*;
import static org.junit.Assert.assertEquals;

public class NameUsageSuggestionServiceEsIT extends EsTestBase {
  static final int DS1 = 100;

  private static NameUsageSuggestionServiceEs service;

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

    EsUtil.refreshIndex(c, cfg.name);
    service = new NameUsageSuggestionServiceEs(cfg.name, c);
  }

  @AfterClass
  public static void deleteTestIndex() throws IOException {
    EsUtil.deleteIndex(esSetup.getClient(), esSetup.getEsConfig().index);
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
    assertEquals("t8", resp.getFirst().getUsageId()); // sorted by relevance - autonym as query term twice
    assertEquals("t7", resp.get(1).getUsageId());
  }

}