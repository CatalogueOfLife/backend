package org.col.common.datapackage;

/**
 *
 */
public class ForeignKey {
  private static final String JSON_KEY_FIELDS = "fields";
  private static final String JSON_KEY_REFERENCE = "reference";
  
  private Object fields = null;
  private Reference reference = null;
  
  public void setFields(Object fields){
    this.fields = fields;
  }
  
  public <Any> Any getFields(){
    return (Any)this.fields;
  }
  
  public void setReference(Reference reference){
    this.reference = reference;
  }
  
  public Reference getReference(){
    return this.reference;
  }
  
}