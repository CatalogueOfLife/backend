package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageRequest.SearchType;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.es.query.Query;

import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class RequestTranslatorTest {

  @Test
  public void generateQuery() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    assertNotNull(RequestTranslator.generateQuery(req));

    req.addFilter(NameUsageSearchParameter.DATASET_KEY, 1010);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setQ("Abies alba");
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setContent(Set.of(NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME));
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setHighlight(true);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setSortBy(NameUsageSearchRequest.SortBy.TAXONOMIC);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setSearchType(SearchType.PREFIX);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setReverse(true);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setFuzzy(true);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.addFilter(NameUsageSearchParameter.USAGE_ID, "abcdef");
    assertNotNull(RequestTranslator.generateQuery(req));
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/729
   */
  @Test
  public void generateWithoutDecision() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, 1010);
    req.addFilter(NameUsageSearchParameter.CATALOGUE_KEY, Datasets.COL);
    req.addFilter(NameUsageSearchParameter.DECISION_MODE, NameUsageRequest.IS_NULL);

    Query q = RequestTranslator.generateQuery(req);
    assertNotNull(q);
  }
}