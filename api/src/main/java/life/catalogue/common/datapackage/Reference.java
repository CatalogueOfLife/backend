package life.catalogue.common.datapackage;

import life.catalogue.coldp.ColdpTerm;

import java.util.Objects;

/**
 *
 *
 */
public class Reference {
  private String resource;
  private String fields;
  
  public Reference() {
  }
  
  public Reference(ColdpTerm resource, ColdpTerm fields) {
    this.resource = resource.simpleName().toLowerCase();
    this.fields = fields.simpleName();
  }
  
  public String getResource() {
    return resource;
  }
  
  public void setResource(String resource) {
    this.resource = resource;
  }
  
  public String getFields() {
    return fields;
  }
  
  public void setFields(String fields) {
    this.fields = fields;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Reference reference = (Reference) o;
    return Objects.equals(resource, reference.resource) &&
        Objects.equals(fields, reference.fields);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(resource, fields);
  }
}