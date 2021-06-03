package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.vocab.Issue;
import life.catalogue.es.EsModule;

import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;

import org.junit.Test;

import static life.catalogue.api.search.NameUsageSearchParameter.*;

/*
 * No real tests here. Just to make sure we don't get exceptions & to peek at the results of specifying an aggregation via the object model.
 */
public class FacetsTranslatorTest {

  @Test
  public void test1() {

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    // Add facets + corresponding filters

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);
    request.addFacet(ORIGIN);

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
    request.setContent(EnumSet.of(SearchContent.AUTHORSHIP));

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(EsModule.writeDebug(translator.translate()));

  }

  @Test
  public void test2() {

    NameUsageSearchRequest request = new NameUsageSearchRequest();

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
    request.setContent(EnumSet.of(SearchContent.AUTHORSHIP));

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(EsModule.writeDebug(translator.translate()));

  }

  @Test
  public void test3() {

    NameUsageSearchRequest request = new NameUsageSearchRequest();

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

    System.out.println(EsModule.writeDebug(translator.translate()));

  }

  @Test
  public void test4() {

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    // Add facets + corresponding filters

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    request.addFilter(ISSUE, Issue.ACCEPTED_ID_INVALID);
    request.addFilter(ISSUE, Issue.BASIONYM_ID_INVALID);
    request.addFilter(ISSUE, Issue.CHAINED_SYNONYM);

    /*
     * Just one filter corresponding to a facet. For that facet, the aggregation should collapse into a simple terms aggregation, while for
     * the others a filter aggregation should be generated.
     */

    // Add non-facet filters
    request.addFilter(NAME_ID, "ABCDEFG");
    request.setQ("anim");
    request.setContent(EnumSet.of(SearchContent.AUTHORSHIP));

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(EsModule.writeDebug(translator.translate()));

  }

  @Test
  public void test5() {

    NameUsageSearchRequest request = new NameUsageSearchRequest();

    request.addFacet(ISSUE);
    request.addFacet(DATASET_KEY);
    request.addFacet(RANK);
    request.addFacet(STATUS);

    FacetsTranslator translator = new FacetsTranslator(request);

    System.out.println(EsModule.writeDebug(translator.translate()));

  }

}
