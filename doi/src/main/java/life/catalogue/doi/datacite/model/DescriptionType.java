package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

public enum DescriptionType implements EnumValue {

  ABSTRACT("Abstract"),
  METHODS("Methods"),
  SERIES_INFORMATION("SeriesInformation"),
  TABLE_OF_CONTENTS("TableOfContents"),
  TECHNICAL_INFO("TechnicalInfo"),
  OTHER("Other");

  private final String value;

  DescriptionType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static DescriptionType fromValue(String v) {
    for (DescriptionType c : DescriptionType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
