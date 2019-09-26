package org.col.es.query;

@SuppressWarnings("unused")
class IsNotNullConstraint extends Constraint {

  private final String field;

  public IsNotNullConstraint(String field) {
    this.field = field;
  }

}
