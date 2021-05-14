package life.catalogue.doi.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import life.catalogue.api.model.DOI;
import life.catalogue.doi.datacite.model.DoiAttributes;

import java.util.Objects;

@JsonTypeName(value = "data")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
public class DataCiteWrapper {

  private static final String type = "dois";
  private DoiAttributes attributes;

  public DataCiteWrapper() {
    attributes = new DoiAttributes();
  }

  public DataCiteWrapper(DOI doi) {
    this.attributes = new DoiAttributes(doi);
  }

  public DataCiteWrapper(DoiAttributes attributes) {
    this.attributes = attributes;
  }

  public DOI getId() {
    return attributes.getDoi();
  }

  public String getType() {
    return type;
  }

  public DoiAttributes getAttributes() {
    return attributes;
  }

  public void setAttributes(DoiAttributes attributes) {
    this.attributes = attributes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DataCiteWrapper)) return false;
    DataCiteWrapper doiData = (DataCiteWrapper) o;
    return Objects.equals(attributes, doiData.attributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attributes);
  }

  @Override
  public String toString() {
    return "DataCiteWrapper{" + attributes.toString() +'}';
  }
}