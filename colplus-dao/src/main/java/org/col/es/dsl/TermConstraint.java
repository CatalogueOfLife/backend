package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
class TermConstraint extends Constraint {

  private final Object value;

  TermConstraint(Object value) {
    this.value = value;
  }

}
