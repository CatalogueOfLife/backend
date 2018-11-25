package org.col.es.translate;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.EsModule;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;

// No real tests here, but generates queries that can be tried out in Kibana.
public class NameSearchRequestTranslatorTest {

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

  @Test
  public void test2() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(DATASET_KEY, 1000);
    nsr.setQ("Caret");

    NameSearchRequestTranslator t = new NameSearchRequestTranslator(nsr, new Page());

    System.out.println(serialize(t.translate()));

  }

  @Test
  public void test3() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.setQ("Caret");

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
