package org.col.es.dsl;

/**
 * Base class for most, but not all {@link Query} implementations. Other {@code Query} implementations extend the
 * AbstractQuery class. So we have two class hierarchies for basically the same thing, which is unfortunate, but
 * inevitable (see {@link Constraint}). Practically all {@code Query} implementations, though, do extend
 * {@code ConstraintQuery}. This abstract base class forces subclasses to return an instance of the type of constraint
 * they host, and uses that to define methods to boost the query and to turn it into a named query.
 *
 * @param <T>
 */
public abstract class ConstraintQuery<T extends Constraint> implements Query {

  @Override
  @SuppressWarnings("unchecked")
  public <Q extends Query> Q withName(String name) {
    getConstraint().name(name);
    return (Q) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Q extends Query> Q withBoost(Float boost) {
    getConstraint().boost(boost);
    return (Q) this;
  }

  // Usually the Constraint instance is a top-level field within the subclasses, but not always, so we can't include
  // the Constraint instance here.
  abstract T getConstraint();

}
