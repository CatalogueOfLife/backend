package org.col.es.dsl;

/**
 * Base class for most (but not all) {@link Query} implementations. Some {@code Query} implementations really just
 * implement the empty {@code Query} interface. See {@link Constraint} for why this is so. Practically all {@code Query}
 * implementations, though, do extend {@code AbstractQuery}. This abstract base class forces subclasses to return an
 * instance of the type of constraint they host, and uses that to define methods to boost the query and to turn it into
 * a named query (so the subclasses won't have to).
 *
 * @param <T>
 */
public abstract class AbstractQuery<T extends Constraint> implements Query {

  /**
   * Turns the query into a named query. Especially useful with compound queries like {@link BoolQuery}. If you name the
   * constraints within a {@code BoolQuery}, the Elasticsearch response will tell you which of those constraints were
   * satisfied for each document in the result set.
   * 
   * @param name
   * @return
   */
  public AbstractQuery<T> withName(String name) {
    getConstraint().name(name);
    return this;
  }

  public AbstractQuery<T> withBoost(Float boost) {
    getConstraint().boost(boost);
    return this;
  }

  // Usually the Constraint instance is a top-level field within the subclasses, but not always, so we can't include
  // the Constraint instance here.
  abstract T getConstraint();

}
