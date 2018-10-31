package org.col.es.query;

/**
 * Represents the "query" part of an Elasticsearch search quest. Since queries can take wildly
 * different forms this is just a tag interface. We need it, however, to be able to nest queries
 * within other queries.
 *
 */
public interface Query {

}
