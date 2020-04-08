package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.es.query.Query;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

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

    req.setPrefixMatchingEnabled(true);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setReverse(true);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.setFuzzyMatchingEnabled(true);
    assertNotNull(RequestTranslator.generateQuery(req));

    req.addFilter(NameUsageSearchParameter.USAGE_ID, "abcdef");
    assertNotNull(RequestTranslator.generateQuery(req));
  }
}