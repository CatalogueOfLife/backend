package org.col.es.translate;

import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.vocab.Issue;
import org.col.es.EsModule;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;

public class FacetsTranslatorTest {

  @Test
  public void test1() {

    NameSearchRequest nsr = new NameSearchRequest();

    // Add facets + corresponding filters

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    nsr.addFilter(DATASET_KEY, 10);
    nsr.addFilter(DATASET_KEY, 12);

    nsr.addFilter(RANK, Rank.KINGDOM);

    // No filter for taxonomic status

    // Add non-facet filters
    nsr.addFilter(NAME_ID, "ABCDEFG");
    nsr.setQ("anim");
    nsr.setContent(EnumSet.of(SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME));

    FacetsTranslator ft = new FacetsTranslator(nsr);

    System.out.println(serialize(ft.translate()));

  }

  @Test
  public void test2() {

    NameSearchRequest nsr = new NameSearchRequest();

    // Add facets + corresponding filters

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    // Just one filter corresponding to a facet. For that facet, the aggregation should collapse into a simple terms aggregation, while for
    // the others a filter aggregation should be generated.

    // Add non-facet filters
    nsr.addFilter(NAME_ID, "ABCDEFG");
    nsr.setQ("anim");
    nsr.setContent(EnumSet.of(SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME));

    FacetsTranslator ft = new FacetsTranslator(nsr);

    System.out.println(serialize(ft.translate()));

  }

  @Test
  public void test3() {

    NameSearchRequest nsr = new NameSearchRequest();

    // Add facets + corresponding filters

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    nsr.addFilter(DATASET_KEY, 10);
    nsr.addFilter(DATASET_KEY, 12);

    // No non-facet filters!

    FacetsTranslator ft = new FacetsTranslator(nsr);

    System.out.println(serialize(ft.translate()));

  }

  @Test
  public void test4() {

    NameSearchRequest nsr = new NameSearchRequest();

    // Add facets + corresponding filters

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    nsr.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    nsr.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    // Just one filter corresponding to a facet. For that facet, the aggregation should collapse into a simple terms aggregation, while for
    // the others a filter aggregation should be generated.

    // Add non-facet filters
    nsr.addFilter(NAME_ID, "ABCDEFG");
    nsr.setQ("anim");
    nsr.setContent(EnumSet.of(SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME));

    FacetsTranslator ft = new FacetsTranslator(nsr);

    System.out.println(serialize(ft.translate()));

  }

  @Test
  public void test5() {

    NameSearchRequest nsr = new NameSearchRequest();

    nsr.addFacet(ISSUE);
    nsr.addFacet(DATASET_KEY);
    nsr.addFacet(RANK);
    nsr.addFacet(STATUS);

    FacetsTranslator ft = new FacetsTranslator(nsr);

    System.out.println(serialize(ft.translate()));

  }

  private static String serialize(Object obj) {
    try {
      return EsModule.MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
