package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

public enum DateType implements EnumValue {

  ACCEPTED("Accepted"),
  AVAILABLE("Available"),
  COLLECTED("Collected"),
  COPYRIGHTED("Copyrighted"),
  CREATED("Created"),
  ISSUED("Issued"),
  OTHER("Other"),
  SUBMITTED("Submitted"),
  UPDATED("Updated"),
  VALID("Valid"),
  WITHDRAWN("Withdrawn");

  private final String value;

  DateType(String v) {
    value = v;
  }

  @Override
  public String value() {
    return value;
  }

  public static DateType fromValue(String v) {
    for (DateType c : DateType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
