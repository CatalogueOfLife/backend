package org.col.es.name.search;

import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.vocab.Issue;
import org.col.es.EsModule;
import org.col.es.name.search.FacetsTranslator;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.col.api.search.NameSearchParameter.DATASET_KEY;
import static org.col.api.search.NameSearchParameter.ISSUE;
import static org.col.api.search.NameSearchParameter.NAME_ID;
import static org.col.api.search.NameSearchParameter.RANK;
import static org.col.api.search.NameSearchParameter.STATUS;

/*
 * No real tests here. Just to make sure we don't get exceptions & to peek at the results of specifying an aggregation
 * via the object model.
 */
public class FacetsTranslatorTest {

  @Test
  public void test1() {

    NameSearchRequest request = new NameSearchRequest();

    // Add facets + corresponding filters

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    request.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    request.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    request.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    request.addFilter(DATASET_KEY, 10);
    request.addFilter(DATASET_KEY, 12);

    request.addFilter(RANK, Rank.KINGDOM);

    // No filter for taxonomic status

    // Add non-facet filters
    request.addFilter(NAME_ID, "ABCDEFG");
    request.setQ("anim");
    request.setContent(EnumSet.of(SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME));

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(serialize(translator.translate()));

  }

  @Test
  public void test2() {

    NameSearchRequest request = new NameSearchRequest();

    // Add facets + corresponding filters

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    request.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    request.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    request.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    // Just one filter corresponding to a facet. For that facet, the aggregation should collapse into a simple terms
    // aggregation, while for
    // the others a filter aggregation should be generated.

    // Add non-facet filters
    request.addFilter(NAME_ID, "ABCDEFG");
    request.setQ("anim");
    request.setContent(EnumSet.of(SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME));

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(serialize(translator.translate()));

  }

  @Test
  public void test3() {

    NameSearchRequest request = new NameSearchRequest();

    // Add facets + corresponding filters

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    request.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    request.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    request.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    request.addFilter(DATASET_KEY, 10);
    request.addFilter(DATASET_KEY, 12);

    // No non-facet filters!

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(serialize(translator.translate()));

  }

  @Test
  public void test4() {

    NameSearchRequest request = new NameSearchRequest();

    // Add facets + corresponding filters

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    request.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    request.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    request.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    // Just one filter corresponding to a facet. For that facet, the aggregation should collapse into a simple terms
    // aggregation, while for
    // the others a filter aggregation should be generated.

    // Add non-facet filters
    request.addFilter(NAME_ID, "ABCDEFG");
    request.setQ("anim");
    request.setContent(EnumSet.of(SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME));

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(serialize(translator.translate()));

  }

  @Test
  public void test5() {

    NameSearchRequest request = new NameSearchRequest();

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(serialize(translator.translate()));

  }

  private static String serialize(Object obj) {
    try {
      return EsModule.MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
