package life.catalogue.es.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.config.IndexConfig;
import life.catalogue.es.EsTestBase;
import life.catalogue.es.EsUtil;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import static life.catalogue.api.search.NameUsageRequest.SearchContent.VERNACULAR_NAME;
import static life.catalogue.es.TestIndexUtils.*;
import static org.junit.Assert.assertEquals;

/**
 * Ranking of vernacular name searches.
 * An exact, whole-string vernacular name match must rank above partial matches,
 * even if the usage has many other vernacular names that dilute its BM25 score.
 * See https://github.com/gbif/taxon-ws/issues/71
 */
public class VernacularSearchIT extends EsTestBase {
  static final int DS = 100;

  private static NameUsageSearchServiceEs service;

  @BeforeClass
  public static void indexTestData() throws Exception {
    ElasticsearchClient c = esSetup.getClient();
    IndexConfig cfg = esSetup.getEsConfig().index;
    EsUtil.deleteIndex(c, cfg.name);
    EsUtil.createIndex(c, cfg);

    // the genus has an exact "Oak", but lots of other names that make the field long
    insert(c, cfg, withVernacular(taxon("q1", "Quercus", "L.", Rank.GENUS, NomCode.BOTANICAL, DS, null, null),
      "eng:Oak", "deu:Eiche", "fre:Chêne", "nld:Eik", "dan:Eg", "pol:Dąb", "hrv:Hrast", "spa:Encinos o robles",
      "por:Carvalhos", "cat:Alzines i roures", "gle:Dair", "cym:Derwen", "bre:Derv", "nob:Eik", "gla:Darach",
      "fin:Tammet", "afr:Akkerbome", "jpn:オーク", "kat:მუხა", "eng:Acorn"
    ));
    // species with short vernacular names that contain "oak", some of them several times
    insert(c, cfg, withVernacular(taxon("q2", "Quercus havardii", "Rydb.", Rank.SPECIES, NomCode.BOTANICAL, DS, null, null),
      "eng:shin oak"
    ));
    insert(c, cfg, withVernacular(taxon("q3", "Quercus douglasii", "Hook. & Arn.", Rank.SPECIES, NomCode.BOTANICAL, DS, null, null),
      "eng:blue oak"
    ));
    insert(c, cfg, withVernacular(taxon("q4", "Quercus durandii", "Buckley", Rank.SPECIES, NomCode.BOTANICAL, DS, null, null),
      "eng:Durand oak", "eng:bluff oak", "eng:bastard white oak", "eng:bastard oak"
    ));
    insert(c, cfg, withVernacular(taxon("q5", "Quercus robur", "L.", Rank.SPECIES, NomCode.BOTANICAL, DS, null, null),
      "eng:English oak", "fre:Chêne pédonculé"
    ));
    insert(c, cfg, withVernacular(taxon("c1", "Casuarina obesa", "Miq.", Rank.SPECIES, NomCode.BOTANICAL, DS, null, null),
      "eng:swamp oak"
    ));

    EsUtil.refreshIndex(c, cfg.name);
    service = new NameUsageSearchServiceEs(cfg.name, c);
  }

  /** Disable base class per-test setup/teardown; the index is shared across all tests. */
  @Override @Before  public void setUp() {}
  @Override @After   public void tearDown() {}

  private List<NameUsageWrapper> search(String q) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setSingleContent(VERNACULAR_NAME);
    req.setQ(q);
    return service.search(req, new Page(0, 100)).getResult();
  }

  @Test
  public void exactMatchFirst() {
    var res = search("oak");
    assertEquals(6, res.size());
    assertEquals("q1", res.getFirst().getId());
  }

  @Test
  public void exactMatchIgnoresCase() {
    assertEquals("q1", search("OAK").getFirst().getId());
    assertEquals("q1", search(" Oak ").getFirst().getId());
  }

  @Test
  public void exactMatchIgnoresAccents() {
    // Quercus robur only has a partial match with "Chêne pédonculé"
    var res = search("chene");
    assertEquals(2, res.size());
    assertEquals("q1", res.getFirst().getId());

    res = search("Chêne");
    assertEquals(2, res.size());
    assertEquals("q1", res.getFirst().getId());
  }

  @Test
  public void exactMultiWordMatchFirst() {
    // "bastard oak" is an exact name of q4, but "oak" alone is also matched by all others
    var res = search("bastard oak");
    assertEquals("q4", res.getFirst().getId());
  }
}
