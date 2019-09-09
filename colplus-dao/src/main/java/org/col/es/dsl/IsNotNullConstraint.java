package org.col.es.dsl;

@SuppressWarnings("unused")
class IsNotNullConstraint extends Constraint {

  private final String field;

  public IsNotNullConstraint(String field) {
    this.field = field;
  }

}
