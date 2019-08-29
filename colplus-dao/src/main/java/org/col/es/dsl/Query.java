package org.col.es.dsl;

/**
 * Represents the "query" part of an Elasticsearch search quest. Since queries can take very different forms this is just a tag interface.
 * We need it, however, to be able to nest queries within other queries, and to nest queries within aggregations.
 */
public interface Query {

}
