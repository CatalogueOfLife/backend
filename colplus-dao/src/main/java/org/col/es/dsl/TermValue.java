package org.col.es.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the (exact) value you are searching for, plus the option to boost matching documents and assign a name to
 * the query constraint (enabling a named query mechanism that will tell you if the constraint was satisfied for a
 * particular document).
 */
@SuppressWarnings("unused")
public class TermValue {

  private final Object value;

  private String _name; // creates named query
  private Float boost;

  TermValue(Object value) {
    this.value = value;
  }

  void name(String name) {
    this._name = name;
  }

  void boost(Float boost) {
    this.boost = boost;
  }

}
