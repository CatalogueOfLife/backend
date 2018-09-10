package org.col.es.mapping;

import java.util.Collection;
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
  @JsonIgnore
  protected ESField parent;
  @JsonIgnore
  protected boolean array;

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

  public ESField getParent() {
    return parent;
  }

  public void setParent(ESField parent) {
    this.parent = parent;
  }

  /**
   * Whether or not the field is multi-valued. There is no such thing as an "array" data type in
   * Elasticsearch. Every field is principally multi-valued. Multiple values can be stored in the
   * same field. However, because the CoL generates Elasticsearch type mappings from {@link Class}
   * objects, we know in advance whether this is actually going to be the case. If the Java field
   * corresponding to an Elasticsearch field is an array or a {@link Collection} object, the
   * Elasticsearch field may contain multiple values. Otherwise it definitely is single-valued.
   * 
   * @return
   */
  public boolean isArray() {
    return array;
  }

  public void setArray(boolean array) {
    this.array = array;
  }

}
