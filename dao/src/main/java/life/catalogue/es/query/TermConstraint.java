package life.catalogue.es.query;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
class TermConstraint extends Constraint {

  private final Object value;

  TermConstraint(Object value) {
    this.value = value;
  }

}
