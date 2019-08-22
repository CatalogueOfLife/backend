package org.col.es.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The data structure within the ES response within which we will find the facets. This is in fact a rather free-style object and it's up to
 * subclasses to provide more detail, but obviously it is meant to contain {@link EsFacet} objects.
 */
public class EsFacetsContainer {

  @JsonProperty("doc_count")
  protected int docCount;

  public EsFacetsContainer() {
    super();
  }

}
