package org.col.es.name.search;

import java.util.Arrays;
import java.util.EnumSet;

import org.col.api.model.Name;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageSearchRequest;
import org.col.api.search.NameUsageSearchRequest.SearchContent;
import org.col.api.search.NameUsageSearchResponse;
import org.col.api.search.NameUsageWrapper;
import org.col.es.EsReadTestBase;
import org.junit.Before;
import org.junit.Test;

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

  @Test
  public void test1() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("Parus maj");
    request.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));

    Name name = new Name();
    name.setGenus("Parus");
    name.setSpecificEpithet("major");
    name.setScientificName("Parus major");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test2() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("PARUS MAJ");
    request.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));

    Name name = new Name();
    name.setGenus("Parus");
    name.setSpecificEpithet("major");
    name.setScientificName("Parus major");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test3() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("PARUS MAJORANA");
    request.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));

    Name name = new Name();
    name.setGenus("Parus");
    name.setSpecificEpithet("major");
    name.setScientificName("Parus major");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(0, response.getResult().size());
  }

  @Test
  public void test4() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("Parus majorana major");
    request.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));

    Name name = new Name();
    name.setGenus("Parus");
    name.setSpecificEpithet("major");
    name.setScientificName("Parus major");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(0, response.getResult().size());
  }

  @Test
  public void test5() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("rosy");
    request.setContent(EnumSet.of(SearchContent.VERNACULAR_NAME));

    // Name, Usage & scientific name to prevent NPEs while indexing
    Name name = new Name();
    name.setScientificName("Foo");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);

    VernacularName vn = new VernacularName();
    vn.setName("Rosy Bee-eater");
    w0.setVernacularNames(Arrays.asList(vn));
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test6() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("EAT");
    request.setContent(EnumSet.of(SearchContent.VERNACULAR_NAME));

    // Name, Usage & scientific name to prevent NPEs while indexing
    Name name = new Name();
    name.setScientificName("Foo");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);

    VernacularName vn = new VernacularName();
    vn.setName("Rosy Bee-eater");
    w0.setVernacularNames(Arrays.asList(vn));
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test7() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("Rosy Bee Eat");
    request.setContent(EnumSet.of(SearchContent.VERNACULAR_NAME));

    // Name, Usage & scientific name to prevent NPEs while indexing
    Name name = new Name();
    name.setScientificName("Foo");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);

    VernacularName vn = new VernacularName();
    vn.setName("Rosy Bee-eater");
    w0.setVernacularNames(Arrays.asList(vn));
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test8() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("Eat Rosy Bee");
    request.setContent(EnumSet.of(SearchContent.VERNACULAR_NAME));

    // Name, Usage & scientific name to prevent NPEs while indexing
    Name name = new Name();
    name.setScientificName("Foo");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);

    VernacularName vn = new VernacularName();
    vn.setName("Rosy Bee-eater");
    w0.setVernacularNames(Arrays.asList(vn));
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

  @Test
  public void test9() {

    // ==> The query
    NameUsageSearchRequest request = new NameUsageSearchRequest();
    request.setQ("Eat Rosy Bee");
    request.setContent(EnumSet.of(SearchContent.VERNACULAR_NAME));

    // Name, Usage & scientific name to prevent NPEs while indexing
    Name name = new Name();
    name.setScientificName("Foo");
    Taxon t = new Taxon();
    t.setName(name);
    NameUsageWrapper w0 = new NameUsageWrapper(t);

    VernacularName vn = new VernacularName();
    vn.setName("ROSY BEE-EATER");
    w0.setVernacularNames(Arrays.asList(vn));
    index(w0);

    NameUsageSearchResponse response = search(request);

    assertEquals(1, response.getResult().size());
  }

}
