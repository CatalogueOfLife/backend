package life.catalogue.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * An object within the ES search response that contains the facets (if we asked for them).
 */
public class FacetValuesContainer {

  private List<Bucket> buckets;
  @JsonProperty("sum_other_doc_count")
  private int sumOtherDocCount;

  public List<Bucket> getBuckets() {
    return buckets;
  }

  public int getSumOtherDocCount() {
    return sumOtherDocCount;
  }

}
