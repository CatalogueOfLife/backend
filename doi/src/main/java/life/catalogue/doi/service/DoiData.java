package life.catalogue.doi.service;

import life.catalogue.api.model.DOI;
import life.catalogue.doi.datacite.model.DoiAttributes;

public class DoiData {
  private static final String type = "dois";
  private DoiAttributes attributes;

  public DoiData() {
    attributes = new DoiAttributes();
  }

  public DoiData(DOI doi) {
    this.attributes = new DoiAttributes(doi);
  }

  public DoiData(DoiAttributes attributes) {
    this.attributes = attributes;
  }

  public DOI getId() {
    return attributes.getDoi();
  }

  public String getType() {
    return type;
  }
}