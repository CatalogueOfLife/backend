package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The data structure within the ES response within which we will find the facets. This is a rather free-style object
 * within the Elasticsearch response (likely just a hash map on the Elasticsearch side), so it's up to subclasses to
 * provide more detail. It is intended, though, to contain {@link EsFacet} objects.
 */
public class Aggregation {

  @JsonProperty("doc_count")
  protected int docCount;

  public Aggregation() {
    super();
  }

}
