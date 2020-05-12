package life.catalogue.es.nu;

import java.io.IOException;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.EsTestUtils;
import static org.junit.Assert.assertEquals;

/*
 * Tests the NameUsageSearchService and the NameUsageSyggestionService for queries on higher taxa using and combinations of fuzzy/exact and
 * prefix/whole-word matching.
 */
public class QTranslationUtilsTest extends EsReadTestBase {

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
    searchQuery.setPrefix(true);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetKey(1008);
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
    searchQuery.setPrefix(true);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetKey(2020);
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
    searchQuery.setPrefix(true);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetKey(2020);
    suggestQuery.setQ("Croco Dundee");
    suggestQuery.setLimit(1000);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(4, nsr.getTotal());
    assertEquals(4, nur.getSuggestions().size());
  }

  @Test
  public void fuzzyAndPrefix() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("morelety");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 123123123);
    searchQuery.setPrefix(true);
    searchQuery.setFuzzy(true);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetKey(123123123);
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
    searchQuery.setPrefix(true);
    searchQuery.setFuzzy(false);
    NameUsageSearchResponse nsr = search(searchQuery);

    NameUsageSuggestRequest suggestQuery = new NameUsageSuggestRequest();
    suggestQuery.setDatasetKey(123123123);
    suggestQuery.setQ("morelety");
    suggestQuery.setLimit(1000);
    suggestQuery.setFuzzy(false);
    NameUsageSuggestResponse nur = suggest(suggestQuery);

    assertEquals(0, nsr.getTotal());
    assertEquals(0, nur.getSuggestions().size());
  }

  @Test
  public void fuzzyAndWholeWordsOnly() throws IOException {
    EsTestUtils.indexCrocodiles(this);

    NameUsageSearchRequest searchQuery = new NameUsageSearchRequest();
    searchQuery.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    searchQuery.setQ("morelety");
    searchQuery.addFilter(NameUsageSearchParameter.DATASET_KEY, 123123123);
    searchQuery.setPrefix(false);
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
    searchQuery.setPrefix(false);
    searchQuery.setFuzzy(false);
    NameUsageSearchResponse nsr = search(searchQuery);

    assertEquals(0, nsr.getTotal());
  }

}
