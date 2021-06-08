package life.catalogue.es.response;

import life.catalogue.es.nu.search.FacetsTranslator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The data structure within the ES search response representing a single facet (or GROUP BY field in SQL speak). It
 * contains all distinct values (groups) of the field and their document counts.
 */
public class EsFacet {

  @JsonProperty("doc_count")
  private int docCount;

  @JsonProperty(FacetsTranslator.FACET_AGG_LABEL)
  private FacetValuesContainer container;

  public int getDocCount() {
    return docCount;
  }

  public FacetValuesContainer getFacetValues() {
    return container;
  }

}
