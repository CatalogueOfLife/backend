package org.col.es.query;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The "global" aggregation is basically a dummy aggregation that resets c.q. bypasses the execution context of the top-level aggregations.
 * Ordinarily top-level aggregations execute within the context provided by the query part of the search request (i.e. they aggregate over
 * the document set produced by that query). By using the global aggregation as the top-level aggregation, the aggregations directly
 * underneath it will aggregate over all documents in the entire index. This allows us to wrap document retrieval and facet retrieval in
 * one and the same search request.
 */
@JsonPropertyOrder({"global", "aggs"})
public class GlobalAggregation extends BucketAggregation {

  @JsonInclude(JsonInclude.Include.ALWAYS)
  final Map<?,?> global = Collections.EMPTY_MAP;

}
