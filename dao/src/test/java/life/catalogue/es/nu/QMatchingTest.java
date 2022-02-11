package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageRequest.SearchType;
import life.catalogue.api.search.*;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.EsTestUtils;

import java.io.IOException;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/*
 * Tests the NameUsageSearchService and the NameUsageSyggestionService for queries on higher taxa using and combinations of fuzzy/exact and
 * prefix/whole-word matching.
 */
public class QMatchingTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void suggestHigherTaxa01() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("Crocodylidae");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 1008);
    searchQuery.setSearchType(SearchType.PREFIX);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetFilter(1008);
    suggestQuery.setQ("Crocodylidae");
    suggestQuery.setLimit(1000);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(nsr.getTotal(), nur.getSuggestions().size());
  }

  @Test
  public void suggestHigherTaxa02() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("Croco");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 2020);
    searchQuery.setSearchType(SearchType.PREFIX);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetFilter(2020);
    suggestQuery.setQ("Croco");
    suggestQuery.setLimit(1000);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(4, nsr.getTotal());
    assertEquals(4, nur.getSuggestions().size());
  }

  @Test
  public void suggestHigherTaxa03() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("Croco Dundee");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 2020);
    searchQuery.setSearchType(SearchType.PREFIX);
    NameUsageSearchResponse nsr = search(searchQuery);
    // Match-type queries currently use operator AND (all search terms must occur in the target field). Therefore:
    assertEquals(0, nsr.getTotal());

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetFilter(2020);
    suggestQuery.setQ("Croco Dundee");
    suggestQuery.setLimit(1000);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(0, nur.getSuggestions().size());
  }

  @Test
  @Ignore("Bad use of ScinameNormalizer in indexing/querying")
  public void fuzzyAndPrefix() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("morelety");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 123123123);
    searchQuery.setSearchType(SearchType.PREFIX);
    searchQuery.setFuzzy(true);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetFilter(123123123);
    suggestQuery.setQ("morelety");
    suggestQuery.setLimit(1000);
    suggestQuery.setFuzzy(true);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(1, nsr.getTotal());
    assertEquals(1, nur.getSuggestions().size());
  }

  @Test
  public void exactAndPrefix() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("morelety");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 123123123);
    searchQuery.setSearchType(SearchType.PREFIX);
    searchQuery.setFuzzy(false);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetFilter(123123123);
    suggestQuery.setQ("morelety");
    suggestQuery.setLimit(1000);
    suggestQuery.setFuzzy(false);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(0, nsr.getTotal());
    assertEquals(0, nur.getSuggestions().size());
  }

  @Test
  @Ignore("Bad use of ScinameNormalizer in indexing/querying")
  public void fuzzyAndWholeWordsOnly() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("morelety");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 123123123);
    searchQuery.setSearchType(SearchType.WHOLE_WORDS);
    searchQuery.setFuzzy(true);
    NameUsageSearchResponse nsr = search(searchQuery);

    assertEquals(1, nsr.getTotal());
  }

  @Test
  public void exactAndWholeWordsOnly() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("morelety");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 123123123);
    searchQuery.setSearchType(SearchType.WHOLE_WORDS);
    searchQuery.setFuzzy(false);
    NameUsageSearchResponse nsr = search(searchQuery);

    assertEquals(0, nsr.getTotal());
  }

}
