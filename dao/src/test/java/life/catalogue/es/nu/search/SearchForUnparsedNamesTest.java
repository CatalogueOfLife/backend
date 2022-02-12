package life.catalogue.es.nu.search;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageRequest.SearchType;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsReadTestBase;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * https://github.com/CatalogueOfLife/backend/issues/541
 */
public class SearchForUnparsedNamesTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {

    String q = "Acholeplasma phage MV-L51 ICTV";

    Name name = new Name();
    name.setScientificName(q);
    Taxon taxon = new Taxon();
    taxon.setName(name);
    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    index(nuw);

    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setSearchType(SearchType.WHOLE_WORDS);

    request.setQ(q);
    NameUsageSearchResponse response = search(request);
    assertEquals(1, response.getTotal());

    q = "MV-L51";
    request.setQ(q);
    response = search(request);
    assertEquals(1, response.getTotal());

    q = "MV-L51 FOOBAR";
    request.setQ(q);
    response = search(request);
    assertEquals(0, response.getTotal());

    q = "MV-L51 MV-L51";
    request.setQ(q);
    response = search(request);
    assertEquals(1, response.getTotal()); // zero results!

    q = "MV-L51 MV-L52";
    request.setQ(q);
    response = search(request);
    assertEquals(0, response.getTotal()); // zero results!

    q = "PHAGE mv-L51";
    request.setQ(q);
    response = search(request);
    assertEquals(1, response.getTotal());

  }

  @Test
  public void test2() {
    String q = "H(eterodon)\n\n  \tlichtensteinii Jan, 1859";

    Name name = new Name();
    name.setScientificName(q);
    Taxon taxon = new Taxon();
    taxon.setName(name);
    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    index(nuw);

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    request.setQ(q);
    NameUsageSearchResponse response = search(request);
    assertEquals(1, response.getTotal());

  }

  @Test
  public void test3() {
    String q = "Acer nigrum × Acer saccharum";

    Name name = new Name();
    name.setScientificName(q);
    Taxon taxon = new Taxon();
    taxon.setName(name);
    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    index(nuw);

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    request.setQ(q);
    NameUsageSearchResponse response = search(request);
    assertEquals(1, response.getTotal());

  }

  @Test
  public void test4() {
    String q = "incertae sedis";

    Name name = new Name();
    name.setScientificName(q);
    Taxon taxon = new Taxon();
    taxon.setName(name);
    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    index(nuw);

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    request.setQ(q);
    NameUsageSearchResponse response = search(request);
    assertEquals(1, response.getTotal());

  }

  @Test
  public void test5() {
    String q = "SH215351.07FU";

    Name name = new Name();
    name.setScientificName(q);
    Taxon taxon = new Taxon();
    taxon.setName(name);
    NameUsageWrapper nuw = new NameUsageWrapper(taxon);
    index(nuw);

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    request.setQ(q);
    NameUsageSearchResponse response = search(request);
    assertEquals(1, response.getTotal());

  }

}
