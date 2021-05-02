package life.catalogue.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.search.FacetValue;

/**
 * The Elasticsearch response object corresponding to the {@link FacetValue} class.
 */
public class Bucket {

  private Object key;
  @JsonProperty("doc_count")
  private int docCount;

  public Object getKey() {
    return key;
  }

  public int getDocCount() {
    return docCount;
  }

}
