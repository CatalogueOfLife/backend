package life.catalogue.es.search;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.common.io.Resources;
import life.catalogue.es.EsTestBase;

import life.catalogue.es.EsUtil;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.parser.NameParser;

import life.catalogue.parser.NomCodeParser;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Parameterized integration test for {@link NameUsageSearchServiceEs} that creates a new index per test
 * and indexes usages based on a tsv file to then test specific parameter combinations reported in issues.
 */
@RunWith(Parameterized.class)
public class NameUsageSearchServiceEs2IT extends EsTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs2IT.class);

  private static final TaxGroupAnalyzer analyzer = new TaxGroupAnalyzer();
  private static final int datasetKey = 100;
  private static NameUsageSearchServiceEs service;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return IntStream.rangeClosed(1, 4)
      .mapToObj(i -> new Object[] {i})
      .collect(Collectors.toList());
  }

  // test param
  private int resource;

  public NameUsageSearchServiceEs2IT(int resource) {
    this.resource = resource;
  }

  @Before
  public void initService() throws Exception {
    service = new NameUsageSearchServiceEs(
      esSetup.getEsConfig().index.name,
      esSetup.getClient()
    );
    // read test data into index
    LOG.info("Indexing test data from resource: {}", resource);
    var stream = Resources.stream("elastic/data/" + resource+".txtree");
    Tree<SimpleTreeNode> tree = Tree.simple(stream);
    LOG.debug("Inserting {} usages", tree.size());
    for (var n : tree.getRoot()) {
      insertSubtree(n, new LinkedList<>());
    }
    EsUtil.refreshIndex(client, indexName);
    LOG.info("Done indexing test data with {} usages", tree.size());
  }

  static TaxonomicStatus status(String x) {return StringUtils.isBlank(x) ? null : TaxonomicStatus.valueOf(x.toUpperCase().trim());};
  static Rank rank(String x) {return StringUtils.isBlank(x) ? null : Rank.valueOf(x.toUpperCase().trim());};
  static NomCode code(String x) {return StringUtils.isBlank(x) ? null : NomCode.valueOf(x.toUpperCase().trim());};

  private void insertSubtree(SimpleTreeNode t, LinkedList<SimpleName> classification) throws InterruptedException {
    insertNode(t,false, classification);
    for (var syn : t.synonyms) {
      insertNode(syn, true, classification);
      classification.removeLast();
    }
    for (var c : t.children) {
      insertSubtree(c, classification);
    }
    classification.removeLast();
  }

  private void insertNode(SimpleTreeNode tn, boolean synonym, LinkedList<SimpleName> classification) {
    try {
      String id;
      if (tn.infos.containsKey(TxtTreeTerm.ID.name())) {
        id = tn.infos.get(TxtTreeTerm.ID.name())[0];
      } else {
        id = String.valueOf(tn.id);
      }
      var issues = IssueContainer.simple();
      var pn = NameParser.PARSER.parse(tn.name, rank(tn.rank), null, issues).get();
      assertFalse(issues.hasIssues());

      var n = pn.getName();
      n.setId(id);
      n.setDatasetKey(datasetKey);
      if (tn.infos.containsKey(TxtTreeTerm.CODE.name())) {
        var code = NomCodeParser.PARSER.parseOrNull(tn.infos.get(TxtTreeTerm.CODE.name())[0]);
        n.setCode(code);
      }

      final var nuw = new NameUsageWrapper();
      final NameUsageBase u;
      if (synonym) {
        u = new Synonym(n);
      } else {
        var t = new Taxon(n);
        u = t;
        var status = TaxonomicStatus.ACCEPTED;
        if (tn.provisional) {
          status = TaxonomicStatus.PROVISIONALLY_ACCEPTED;
        }
        u.setStatus(status);
        if (tn.extinct) {
          t.setExtinct(true);
        }
        if (tn.infos.containsKey(TxtTreeTerm.VERN.name())) {
          if (tn.infos.containsKey(TxtTreeTerm.CODE.name())) {
            var code = NomCodeParser.PARSER.parseOrNull(tn.infos.get(TxtTreeTerm.CODE.name())[0]);
            n.setCode(code);
          }
          nuw.setVernacularNames(new ArrayList<>());
          for (var x : tn.infos.get(TxtTreeTerm.VERN.name())) {
            var parts = x.split(":");
            var vn = new SimpleVernacularName();
            vn.setLanguage(parts[0]);
            vn.setName(parts[1]);
            nuw.getVernacularNames().add(vn);
          }
        }
      }
      u.setId(id);
      u.setDatasetKey(datasetKey);
      u.setOrigin(Origin.SOURCE);
      nuw.setUsage(u);
      if (classification != null && !classification.isEmpty()) {
        u.setParentId(classification.getLast().getId());
      }

      var sn = new SimpleName((NameUsageBase)nuw.getUsage());
      classification.add(sn);
      nuw.setClassification(classification);
      nuw.setGroup(analyzer.analyze(classification.getLast(), classification));
      EsUtil.insert(client, cfg.index.name, nuw);

    } catch (Exception e) {
      System.err.println(tn);
      throw new RuntimeException(e);
    }
  }

  // -----------------------------------------------------------------------
  // Search helpers
  // -----------------------------------------------------------------------

  private NameUsageSearchResponse search(NameUsageSearchRequest req) {
    return service.search(req, new Page(0, 100));
  }

  private int count(NameUsageSearchRequest req) {
    return search(req).getTotal();
  }

  // -----------------------------------------------------------------------
  // Tests
  // -----------------------------------------------------------------------

  NameUsageSearchResponse search(String q, NameUsageRequest.SearchType type, NameUsageRequest.SortBy sortBy) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setDatasetFilter(datasetKey);
    req.setQ(q);
    req.setSearchType(type);
    req.setSortBy(sortBy);
    return search(req);
  }

  @Test
  public void testData() {
    var req = new NameUsageSearchRequest();
    int cnt = count(req);
    req.setDatasetFilter(datasetKey);
    assertEquals(cnt, count(req)); // all from the same dataset

    NameUsageSearchResponse resp;

    switch (resource) {
      case 1:
        // https://github.com/CatalogueOfLife/backend/issues/1086
        resp = search("Lutra lutra", NameUsageRequest.SearchType.STANDARD, NameUsageRequest.SortBy.RELEVANCE);
        assertName(resp.getResult().getFirst(), Rank.SPECIES, "Lutra lutra", "(Linnaeus, 1758)");
        assertID(resp.getResult().getFirst(), "72PQL");
        break;

      case 2:
        // https://github.com/CatalogueOfLife/backend/issues/1466
        resp = search("Crumenaria polygaloides lancifolia", NameUsageRequest.SearchType.STANDARD, NameUsageRequest.SortBy.RELEVANCE);
        assertID(resp.getResult().getFirst(), "V56F2"); // the only accepted

        resp = search("Crumenaria polygaloides var. lancifolia", NameUsageRequest.SearchType.STANDARD, NameUsageRequest.SortBy.RELEVANCE);
        assertID(resp.getResult().getFirst(), "V56F2"); // the only accepted
        break;

      case 3:
        // https://github.com/CatalogueOfLife/backend/issues/1513
        // Browsing everything below Puma concolor (no q) must list the accepted subspecies first
        // and all synonyms below, regardless of their rank.
        var browseReq = new NameUsageSearchRequest();
        browseReq.setDatasetFilter(datasetKey);
        browseReq.addFilter(NameUsageSearchParameter.TAXON_ID, "PCON");
        resp = search(browseReq);

        assertAcceptedBeforeSynonyms(resp.getResult());
        break;

      case 4:
        // https://github.com/CatalogueOfLife/checklistbank/issues/1688
        // An exact name match must rank at the very top regardless of taxonomic status and rank.
        // "Lepisma" is both an accepted genus (GACC) and a synonym homonym (GSYN); both must
        // outrank the non-exact accepted matches (Lepisma saccharina, Lepismatidae).
        // Within the exact matches the accepted one still comes before the synonym.
        resp = search("Lepisma", NameUsageRequest.SearchType.STANDARD, NameUsageRequest.SortBy.RELEVANCE);
        List<String> ids = resp.getResult().stream().map(NameUsageWrapper::getId).collect(Collectors.toList());

        assertEquals("exact accepted match must rank first", "GACC", ids.get(0));
        assertEquals("exact synonym match must rank second, above non-exact accepted names", "GSYN", ids.get(1));
        assertTrue("exact synonym must rank above the non-exact accepted species",
          ids.indexOf("GSYN") < ids.indexOf("SACC"));
        assertTrue("exact synonym must rank above the non-exact accepted family",
          ids.indexOf("GSYN") < ids.indexOf("FAM"));

        // exact matching must be case insensitive: a lowercase query ranks the same exact matches on top
        resp = search("lepisma", NameUsageRequest.SearchType.STANDARD, NameUsageRequest.SortBy.RELEVANCE);
        List<String> lcIds = resp.getResult().stream().map(NameUsageWrapper::getId).collect(Collectors.toList());
        assertEquals("case-insensitive exact accepted match must rank first", "GACC", lcIds.get(0));
        assertEquals("case-insensitive exact synonym match must rank second", "GSYN", lcIds.get(1));
        break;

    }
  }

  /**
   * Asserts the result is partitioned: every accepted usage comes before every synonym,
   * i.e. once a synonym is seen no accepted usage may follow.
   */
  static void assertAcceptedBeforeSynonyms(List<NameUsageWrapper> results) {
    long accepted = results.stream().filter(r -> r.getUsage().getStatus().isTaxon()).count();
    long synonyms = results.stream().filter(r -> r.getUsage().getStatus().isSynonym()).count();
    assertEquals("expected the 3 accepted usages (Puma concolor + 2 subspecies)", 3, accepted);
    assertEquals("expected 6 synonyms", 6, synonyms);

    boolean seenSynonym = false;
    for (NameUsageWrapper r : results) {
      if (r.getUsage().getStatus().isSynonym()) {
        seenSynonym = true;
      } else if (seenSynonym) {
        fail("accepted " + r.getUsage().getLabel() + " ranked below a synonym");
      }
    }
  }

  void assertName(NameUsageWrapper nuw, Rank rank, String name, String authorship) {
    assertEquals(name, nuw.getUsage().getName().getScientificName());
    if (authorship != null) {
      assertEquals(authorship, nuw.getUsage().getName().getAuthorship());
    }
    if (rank != null) {
      assertEquals(rank, nuw.getUsage().getName().getRank());
    }
  }

  void assertID(NameUsageWrapper nuw, String id) {
    assertEquals(id, nuw.getId());
  }

}
