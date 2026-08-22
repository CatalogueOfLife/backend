package life.catalogue.exporter;

import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.Rank;

import java.net.URI;

import org.junit.Test;

import static org.junit.Assert.*;

public class SearchExportReadmeTest {

  @Test
  public void searchUrl() {
    var req = new NameUsageSearchRequest();
    req.setQ("abies");
    req.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);
    req.addFilter(NameUsageSearchParameter.ISSUE, Issue.ESCAPED_CHARACTERS);
    req.addFilter(NameUsageSearchParameter.EXTINCT, NameUsageRequest.IS_NULL);
    req.setSortBy(NameUsageSearchRequest.SortBy.RELEVANCE);

    URI uri = SearchExportReadme.searchUrl(URI.create("https://www.checklistbank.org"), 315192, req);
    assertNotNull(uri);
    String s = uri.toString();
    // points at the CLB UI search page of the right dataset
    assertTrue(s, s.startsWith("https://www.checklistbank.org/dataset/315192/names?"));
    // filters serialized with their parameter enum names and canonical values
    assertTrue(s, s.contains("RANK=SPECIES"));
    assertTrue(s, s.contains("ISSUE=ESCAPED_CHARACTERS"));
    assertTrue(s, s.contains("EXTINCT=" + NameUsageRequest.IS_NULL));
    assertTrue(s, s.contains("q=abies"));
    assertTrue(s, s.contains("sortBy=RELEVANCE"));
    // the default search content is included
    assertTrue(s, s.contains("content=SCIENTIFIC_NAME"));
  }

  @Test
  public void searchUrlNoClbUri() {
    assertNull(SearchExportReadme.searchUrl(null, 3, new NameUsageSearchRequest()));
  }
}
