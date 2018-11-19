package org.col.es.query;

/**
 * Represents the "aggs" part of an Elasticsearch search quest. Since aggregations can take wildly different forms this is currently just a
 * tag interface. We need it, however, to be able to nest aggregations within other aggregations.
 */
public interface Aggregation {

}
