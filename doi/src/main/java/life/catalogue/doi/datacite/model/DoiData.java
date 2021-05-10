package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.DOI;

public class DoiData {
  private static final String type = "dois";
  private Attributes attributes;

  public DoiData() {
    attributes = new Attributes();
  }

  public DoiData(DOI doi) {
    this.attributes = new Attributes(doi);
  }

  public DoiData(Attributes attributes) {
    this.attributes = attributes;
  }

  public DOI getId() {
    return attributes.getDoi();
  }

  public String getType() {
    return type;
  }
}