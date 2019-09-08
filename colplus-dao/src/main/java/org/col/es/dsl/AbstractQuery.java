package org.col.es.dsl;

public abstract class AbstractQuery<T extends Constraint> implements Query {

  public AbstractQuery<T> withName(String name) {
    getConstraint().name(name);
    return this;
  }

  public AbstractQuery<T> withBoost(Float boost) {
    getConstraint().boost(boost);
    return this;
  }

  abstract T getConstraint();

}
