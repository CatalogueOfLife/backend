package org.col.common.datapackage;

import java.util.Objects;

import org.col.api.datapackage.ColTerm;

/**
 *
 */
public class ForeignKey {
  private String fields;
  private Reference reference;
  
  public ForeignKey() {
  }
  
  public ForeignKey(ColTerm field, ColTerm resource, ColTerm resourceField) {
    this.fields = field.simpleName();
    reference = new Reference(resource, resourceField);
  }
  
  public String getFields() {
    return fields;
  }
  
  public void setFields(String fields) {
    this.fields = fields;
  }
  
  public Reference getReference() {
    return reference;
  }
  
  public void setReference(Reference reference) {
    this.reference = reference;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ForeignKey that = (ForeignKey) o;
    return Objects.equals(fields, that.fields) &&
        Objects.equals(reference, that.reference);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(fields, reference);
  }
}