package org.col.es.mapping;

/**
 * A {@code SimpleField} is a field with a simple data type. In other words it is not an object
 * containing other fields.
 */
public class SimpleField extends ESField {
  
  protected Boolean index;
  
  public SimpleField(ESDataType type) {
    this.type = type;
  }
  
  public Boolean getIndex() {
    return index;
  }
  
  public void setIndex(Boolean index) {
    this.index = index;
  }
  
}
