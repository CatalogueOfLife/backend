package life.catalogue.es2.suggest;

import life.catalogue.api.model.SimpleName;
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
    assertEquals(5, resp.size());
  }

}