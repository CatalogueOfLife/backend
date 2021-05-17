package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

public enum EventType implements EnumValue {

  /**
   * Triggers a state move from draft or registered to findable
   */
  PUBLISH("publish"),

  /**
   * Triggers a state move from draft to registered
   */
  REGISTER("register"),

  /**
   * Triggers a state move from findable to registered
   */
  HIDE("hide");

  private final String value;

  EventType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static EventType fromValue(String v) {
    for (EventType c : EventType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
