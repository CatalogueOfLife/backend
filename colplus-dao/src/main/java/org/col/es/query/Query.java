package org.col.es.query;

/**
 * Represents the "query" part of an Elasticsearch search quest. Since queries can take very different forms this is just a tag interface.
 * We need it, however, to be able to nest queries within other queries, and to nest queries wothin aggregations.
 */
public interface Query {

}
