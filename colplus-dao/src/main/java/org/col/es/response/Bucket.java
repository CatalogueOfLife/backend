package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.col.api.search.FacetValue;

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
