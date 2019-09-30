package org.col.es.query;

/**
 * Abstract base class for a rather small minority of {@link Query} implementations. See {@link Constraint} and
 * {@link ConstraintQuery}.
 */
@SuppressWarnings("unused")
public class AbstractQuery implements Query {

  private String name;
  private Double boost;

  @Override
  @SuppressWarnings("unchecked")
  public <Q extends Query> Q withName(String name) {
    this.name = name;
    return (Q) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Q extends Query> Q withBoost(Double boost) {
    this.boost = boost;
    return (Q) this;
  }

}
