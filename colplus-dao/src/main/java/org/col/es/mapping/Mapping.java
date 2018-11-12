package org.col.es.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Models an Elasticsearch document type mapping.
 */
@JsonPropertyOrder({"dynamic", "properties"})
public class Mapping<T> extends ComplexField {
  
  /* Always use strict typing */
  private final String dynamic = "strict";
  @JsonIgnore
  private final Class<T> mappedClass;
  
  Mapping(Class<T> mappedClass) {
    this.mappedClass = mappedClass;
  }
  
  /**
   * Returns the value of the type mapping's "dynamic" property. Since all COL document types are
   * strictly typed, this method will always return "strict".
   */
  public String getDynamic() {
    return dynamic;
  }
  
  /**
   * Returns Java class from which the Elasticsearch mapping was generated. generated.
   */
  public Class<T> getMappedClass() {
    return mappedClass;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || obj.getClass() != Mapping.class) {
      return false;
    }
    Mapping<?> other = (Mapping<?>) obj;
    return mappedClass == other.mappedClass;
  }
  
  @Override
  public int hashCode() {
    return mappedClass.hashCode();
  }
  
}
