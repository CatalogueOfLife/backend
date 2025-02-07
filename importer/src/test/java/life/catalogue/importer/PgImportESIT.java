package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.io.TempFile;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.es.EsSetupRule;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.query.Query;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static life.catalogue.api.vocab.DataFormat.COLDP;
import static org.junit.Assert.*;

/**
 * Integration tests of PgImport focussing on a real search index and making sure things get indexed properly.
 */
public class PgImportESIT extends PgImportITBase {

  NameUsageSearchServiceEs searchService;

  @ClassRule
  public static final EsSetupRule esSetupRule = new EsSetupRule();


  @Before
  public void initEs() {
    indexService = new NameUsageIndexServiceEs(
      esSetupRule.getClient(),
      esSetupRule.getEsConfig(),
      TempFile.directoryFile(),
      SqlSessionFactoryRule.getSqlSessionFactory());
    searchService = new NameUsageSearchServiceEs(esSetupRule.getEsConfig().nameUsage.name, esSetupRule.getClient());
  }

  NameUsageSearchResponse search(NameUsageSearchRequest query) {
    return searchService.search(query, new Page(Page.MAX_LIMIT));
  }

  /**
   * Executes the provided query against the text index. The number of returned documents is capped on {Page#MAX_LIMIT Page.MAX_LIMIT}, so
   * make sure the provided query will yield less documents.
   */
  NameUsageSearchResponse query(Query query) throws IOException {
    EsSearchRequest req = EsSearchRequest.emptyRequest().where(query).size(Page.MAX_LIMIT);
    return searchService.search(esSetupRule.getEsConfig().nameUsage.name, req, new Page(Page.MAX_LIMIT));
  }

  @Test
  public void testColdpSpecs() throws Exception {
    normalizeAndImport(COLDP, 0);
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.setSingleContent(NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME);
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, dataset.getKey());
    req.addFacet(NameUsageSearchParameter.STATUS);
    req.addFacet(NameUsageSearchParameter.ISSUE);
    req.addFacet(NameUsageSearchParameter.DECISION_MODE);

    // test facets for all
    NameUsageSearchResponse resp = search(req);
    assertEquals(30, resp.getTotal());

    Set<FacetValue<?>> facet = resp.getFacets().get(NameUsageSearchParameter.STATUS);
    assertEquals(4, facet.size());
    assertFacetValue(facet, TaxonomicStatus.ACCEPTED, 22);
    assertFacetValue(facet, TaxonomicStatus.PROVISIONALLY_ACCEPTED, 1);
    assertFacetValue(facet, TaxonomicStatus.SYNONYM, 5);
    assertFacetValue(facet, TaxonomicStatus.BARE_NAME, 2);

    facet = resp.getFacets().get(NameUsageSearchParameter.ISSUE);
    assertEquals(4, facet.size());
    assertFacetValue(facet, Issue.INCONSISTENT_NAME, 1);
    assertFacetValue(facet, Issue.PARTIAL_DATE, 2);
    assertFacetValue(facet, Issue.PARENT_SPECIES_MISSING, 1);
    assertFacetValue(facet, Issue.RANK_NAME_SUFFIX_CONFLICT, 1);

    facet = resp.getFacets().get(NameUsageSearchParameter.DECISION_MODE);
    assertEquals(0, facet.size());

    // test subspecies classification
    req.addFilter(NameUsageSearchParameter.USAGE_ID, "1001b");
    resp = search(req);
    assertEquals(1, resp.getTotal());
    NameUsageWrapper nuw = resp.getResult().get(0);
    assertTrue(nuw.getUsage().isTaxon());
    assertEquals("1001b", nuw.getUsage().getId());
    assertEquals(Rank.SUBSPECIES, nuw.getUsage().getName().getRank());
    assertEquals("Crepis bakeri subsp. cusickii", nuw.getUsage().getName().getScientificName());
    assertEquals("(Eastw.) Babc. & Stebbins", nuw.getUsage().getName().getAuthorship());
    assertEquals("Crepis bakeri subsp. cusickii (Eastw.) Babc. & Stebbins", nuw.getUsage().getLabel());
    assertEquals("1001", ((NameUsageBase) nuw.getUsage()).getParentId());
    assertEquals(7, nuw.getClassification().size());
    assertEquals(List.of(
      new SimpleName("1", "Plantae", Rank.KINGDOM),
      new SimpleName("10", "Asteraceae", Rank.FAMILY),
      new SimpleName("20", "Cichorioideae", Rank.SUBFAMILY),
      new SimpleName("30", "Cichorieae", Rank.TRIBE),
      new SimpleName("101", "Crepis", Rank.GENUS),
      new SimpleName("1001", "Crepis bakeri", Rank.SPECIES),
      new SimpleName("1001b", "Crepis bakeri subsp. cusickii", Rank.SUBSPECIES)
    ), nuw.getClassification());

    // test synonym classification
    req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, dataset.getKey());
    req.setFilter(NameUsageSearchParameter.USAGE_ID, "1006-1006-s3");
    resp = search(req);
    assertEquals(1, resp.getTotal());
    nuw = resp.getResult().get(0);
    assertEquals("1006-1006-s3", nuw.getUsage().getId());
    assertTrue(nuw.getUsage().isSynonym());
    assertEquals(Rank.SPECIES, nuw.getUsage().getName().getRank());
    assertEquals("Leonida taraxacoida", nuw.getUsage().getName().getScientificName());
    assertEquals("Vill.", nuw.getUsage().getName().getAuthorship());
    assertEquals("Leonida taraxacoida Vill.", nuw.getUsage().getLabel());
    assertEquals("1006", ((NameUsageBase) nuw.getUsage()).getParentId());
    assertEquals(7, nuw.getClassification().size());
    assertEquals(List.of(
      new SimpleName("1", "Plantae", Rank.KINGDOM),
      new SimpleName("10", "Asteraceae", Rank.FAMILY),
      new SimpleName("20", "Cichorioideae", Rank.SUBFAMILY),
      new SimpleName("30", "Cichorieae", Rank.TRIBE),
      new SimpleName("102", "Leontodon", Rank.GENUS),
      new SimpleName("1006", "Leontodon taraxacoides", Rank.SPECIES),
      new SimpleName("1006-1006-s3", "Leonida taraxacoida", Rank.SPECIES)
    ), nuw.getClassification());

    Taxon acc = ((Synonym) nuw.getUsage()).getAccepted();
    assertEquals("1006", acc.getId());
    assertEquals("Leontodon taraxacoides", acc.getName().getScientificName());
    assertEquals("(Vill.) Mérat", acc.getName().getAuthorship());
    assertEquals("Leontodon taraxacoides (Vill.) Mérat", acc.getLabel());

    // test accordingTo
    req.setFilter(NameUsageSearchParameter.USAGE_ID, "Jarvis2007");
    resp = search(req);
    nuw = resp.getResult().get(0);
    assertEquals("Jarvis2007", nuw.getUsage().getId());
    assertTrue(nuw.getUsage().isTaxon());
    assertEquals(Rank.SPECIES, nuw.getUsage().getName().getRank());
    assertEquals("Gundelia tournefortii", nuw.getUsage().getName().getScientificName());
    assertEquals("L.", nuw.getUsage().getName().getAuthorship());
    assertNull(nuw.getUsage().getNamePhrase());
    assertEquals("Jarvis2007", nuw.getUsage().getAccordingToId());
    assertEquals("Jarvis, & Charlie. (2007). Order out of Chaos. Linnaean Plant Types and their Types. Linnaean Society of London in association with the Natural History Museum. https://doi.org/10.5281/zenodo.291971", ((NameUsageBase) nuw.getUsage()).getAccordingTo());
    assertEquals("Gundelia tournefortii L. sensu Jarvis 2007", nuw.getUsage().getLabel());
  }

  void assertFacetValue(Set<FacetValue<?>> facets, Enum<?> value, int count) {
    for (FacetValue<?> fv : facets) {
      if (fv.getValue().equals(value)) {
        assertEquals("Wrong facet value count for " + value, count, fv.getCount());
        return;
      }
    }
    fail("No facet value for " + value);
  }
}
