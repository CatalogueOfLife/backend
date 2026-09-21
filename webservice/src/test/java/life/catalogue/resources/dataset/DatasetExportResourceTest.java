package life.catalogue.resources.dataset;

import life.catalogue.api.search.NameUsageRequest.SearchContent;
import life.catalogue.api.search.NameUsageRequest.SearchType;
import life.catalogue.api.search.NameUsageRequest.SortBy;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;

import org.gbif.nameparser.api.Rank;

import java.util.Set;

import org.junit.Test;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DatasetExportResourceTest {

  /**
   * The query as jersey binds it for the GET search: every query parameter declared on the request class.
   */
  private static NameUsageSearchRequest bound() {
    var query = new NameUsageSearchRequest();
    query.setQ("Abies");
    query.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));
    query.setSearchType(SearchType.EXACT);
    query.setSortBy(SortBy.NAME);
    query.setReverse(true);
    query.setMinRank(Rank.SPECIES);
    return query;
  }

  private static MultivaluedMap<String, String> params() {
    var qp = new MultivaluedHashMap<String, String>();
    qp.add("q", "Abies");
    qp.add("content", "SCIENTIFIC_NAME");
    qp.add("type", "EXACT");
    qp.add("sortBy", "NAME");
    qp.add("reverse", "true");
    qp.add("minRank", "species");
    qp.add("rank", "species");
    return qp;
  }

  private static void assertAbiesSpecies(NameUsageSearchRequest req) {
    assertEquals("Abies", req.getQ());
    assertEquals(Set.of(SearchContent.SCIENTIFIC_NAME), req.getContent());
    assertEquals(SearchType.EXACT, req.getSearchType());
    assertEquals(SortBy.NAME, req.getSortBy());
    assertTrue(req.isReverse());
    assertEquals(Rank.SPECIES, req.getMinRank());
    assertEquals(Set.of(Rank.SPECIES), req.getFilterValues(NameUsageSearchParameter.RANK));
  }

  /**
   * A plain API user puts the whole search into the query string, just like for the GET search.
   * q, content, type, sortBy and reverse used to be dropped, so a q=Abies download exported the entire dataset.
   */
  @Test
  public void queryStringOnly() {
    assertAbiesSpecies(DatasetExportResource.searchRequest(null, bound(), params()));
  }

  @Test
  public void bodyOnly() {
    var body = bound();
    body.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);
    assertAbiesSpecies(DatasetExportResource.searchRequest(body, new NameUsageSearchRequest(), new MultivaluedHashMap<>()));
  }

  /** A parameter given in the query string wins over the body, everything else the body sets is kept. */
  @Test
  public void queryStringOverridesBody() {
    var body = new NameUsageSearchRequest();
    body.setQ("Pinus");
    body.setSearchType(SearchType.EXACT);
    body.setSortBy(SortBy.NAME);
    body.setReverse(true);
    body.setMinRank(Rank.SPECIES);
    body.setContent(Set.of(SearchContent.SCIENTIFIC_NAME));

    var query = new NameUsageSearchRequest();
    query.setQ("Abies");
    var qp = new MultivaluedHashMap<String, String>();
    qp.add("q", "Abies");
    qp.add("rank", "species");
    assertAbiesSpecies(DatasetExportResource.searchRequest(body, query, qp));
  }
}
