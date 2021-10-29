package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

public enum NameType implements EnumValue {

  ORGANIZATIONAL("Organizational"),
  PERSONAL("Personal");

  private final String value;

  NameType(String v) {
    value = v;
  }

  @Override
  public String value() {
    return value;
  }

  public static NameType fromValue(String v) {
    for (NameType c : NameType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
