package life.catalogue.es.nu.search;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageRequest.SearchType;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsReadTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/**
 * Tests with only a Q (search phrase) parameter.
 *
 */
public class QSearchTests extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  NameUsageWrapper indexParusMajor(){
    Name name = new Name();
    name.setGenus("Parus");
    name.setSpecificEpithet("major");
    name.setScientificName("Parus major");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);
    index(w0);
    return w0;
  }

  @Test
  public void content() {
    indexParusMajor();

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setQ("Parus maj");
    query.setContent(null);
    query.setSearchType(SearchType.PREFIX);

    assertEquals(1, search(query).getResult().size());

    query.setContent(EnumSet.noneOf(SearchContent.class));
    assertEquals(1, search(query).getResult().size());

    query.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));
    assertEquals(1, search(query).getResult().size());

    query.setContent(EnumSet.of(SearchContent.AUTHORSHIP));
    assertEquals(0, search(query).getResult().size());

    query.getContent().add(SearchContent.SCIENTIFIC_NAME);
    assertEquals(1, search(query).getResult().size());
  }

  @Test
  public void test1() {
    indexParusMajor();

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setQ("Parus maj");
    query.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));
    query.setSearchType(SearchType.PREFIX);

    NameUsageSearchResponse response = search(query);

    assertEquals(1, response.getResult().size());

    query.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));
    response = search(query);
    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test2() {
    indexParusMajor();

    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setQ("PARUS MAJ");
    query.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));
    query.setSearchType(SearchType.PREFIX);

    NameUsageSearchResponse response = search(query);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test3() {
    indexParusMajor();

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("PARUS MAJORANA");
    request.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));

    NameUsageSearchResponse response = search(request);

    assertEquals(0, response.getResult().size());
  }

  @Test
  public void test4() {
    indexParusMajor();

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("Parus majorana major");
    request.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));

    NameUsageSearchResponse response = search(request);

    assertEquals(0, response.getResult().size());
  }

  @Test // EXACT matching
  public void test10() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("COVID-19");
    request.setSearchType(SearchType.EXACT);

    // Name, Usage & scientific name to prevent NPEs while indexing
    Name name = new Name();
    name.setScientificName("COVID-19");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);
    index(w0);

    name = new Name();
    name.setScientificName("covid-19");
    t = new Taxon();
    t.setName(name);
    w0 = new NameUsageWrapper(t);
    index(w0);

    name = new Name();
    name.setScientificName("Covid-19");
    t = new Taxon();
    t.setName(name);
    w0 = new NameUsageWrapper(t);
    index(w0);

    name = new Name();
    name.setScientificName("Covid 19");
    t = new Taxon();
    t.setName(name);
    w0 = new NameUsageWrapper(t);
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
    assertEquals("COVID-19",response.getResult().get(0).getUsage().getName().getScientificName());
  }

}
