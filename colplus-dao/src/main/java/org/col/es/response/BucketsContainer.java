package org.col.es.response;

import java.util.List;

/**
 * An object within the ES search response that contains the facets (if we asked for them).
 */
public class BucketsContainer {

  private List<Bucket> buckets;

  public List<Bucket> getBuckets() {
    return buckets;
  }

}
