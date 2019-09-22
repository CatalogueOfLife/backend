package org.col.es.dsl;

/**
 * Represents the "query" part of an Elasticsearch search quest. Since queries can take very different forms, the only
 * aspects of a query this interface defines are its name and its boost value.
 */
public interface Query {

  /**
   * Turns the query into a named query. Fluent interface. Named queries are especially useful with compound queries like
   * {@link BoolQuery}. If you name the constraints within a {@code BoolQuery}, the Elasticsearch response will tell you
   * which of those constraints were satisfied for each document in the result set.
   * 
   * @param name
   * @return
   */
  public <Q extends Query> Q withName(String name);

  /**
   * Sets a boost value for documents matching this query. Fluent interface.
   * 
   * @param boost
   * @return
   */
  public <Q extends Query> Q withBoost(Double boost);

}
