package org.col.es.translate;

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
import static org.col.api.search.NameSearchParameter.*;

// No real tests here, but generates queries that can be tried out in Kibana.
public class NameSearchRequestTranslatorTest {

  /*
   * Case: 4 facets, two filters, both corresponding to a facet.
   */
  @Test
  public void test1() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
    nsr.addFilter(RANK, Rank.GENUS);

    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, new Page());

    System.out.println(serialize(t.translate()));

  }

  /*
   * Case: 4 facets, three filters, one corresponding to a facet, two non-facet filters.
   */
  @Test
  public void test2() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
//    nsr.addFilter(PUBLISHED_IN_ID, "ABCD");
    nsr.setQ("Car");

    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, new Page());

    System.out.println(serialize(t.translate()));

  }

  /*
   * Case: 3 facets, two non-facet filters.
   */
  @Test
  public void test3() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);

    nsr.addFilter(STATUS, TaxonomicStatus.ACCEPTED);
    nsr.setQ("c");

    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, new Page());

    System.out.println(serialize(t.translate()));

  }

  /*
   * Case: 4 factes, no filters.
   */
  @Test
  public void test4() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, new Page());

    System.out.println(serialize(t.translate()));

  }

  /*
   * Case: 1 facet, two non-facet filters
   */
  @Test
  public void test5() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(RANK);
    
    nsr.addFilter(DATASET_KEY, 1000);
    nsr.setQ("Car");
    
    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, new Page());

    System.out.println(serialize(t.translate()));

  }

  private static String serialize(Object obj) {
    try {
      return EsModule.QUERY_WRITER.withDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
