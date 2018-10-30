package org.col.es.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Abstract base class for all nodes within a mapping. The {@link Mapping} object itself, any nested
 * {@link ComplexField documents} within it, all {@link SimpleField fields} and all
 * {@link MultiField multi-fields} underneath a field are instances of an {@link ESField}.
 */
public abstract class ESField {

  @JsonIgnore
  protected String name;
  protected ESDataType type;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ESDataType getType() {
    return type;
  }

  public void setType(ESDataType type) {
    this.type = type;
  }


}
