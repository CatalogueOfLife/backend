package org.col.es.name.search;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.vocab.TaxonomicStatus;
import org.col.es.EsModule;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;

// No real tests here, but generates queries that can be tried out in Kibana.
public class RequestTranslatorTest {

  /*
   * Case: 4 facets, two filters, both corresponding to a facet.
   */
  @Test
  public void test1() throws JsonProcessingException {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
    nsr.addFilter(RANK, Rank.GENUS);

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 4 facets, three filters, one corresponding to a facet, two non-facet filters.
   */
  @Test
  public void test2() throws JsonProcessingException {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
    // nsr.addFilter(PUBLISHED_IN_ID, "ABCD");
    nsr.setQ("Car");

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 3 facets, two non-facet filters.
   */
  @Test
  public void test3() throws JsonProcessingException {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);

    nsr.addFilter(STATUS, TaxonomicStatus.ACCEPTED);
    nsr.setQ("c");

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 4 facets, no filters.
   */
  @Test
  public void test4() throws JsonProcessingException {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

  /*
   * Case: 1 facet, two non-facet filters
   */
  @Test
  public void test5() throws JsonProcessingException {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(RANK);

    nsr.addFilter(DATASET_KEY, 1000);
    nsr.setQ("Car");

    RequestTranslator t = new RequestTranslator(nsr, new Page());

    System.out.println(EsModule.write(t.translate()));

  }

}
