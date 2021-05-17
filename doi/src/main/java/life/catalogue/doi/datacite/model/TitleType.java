package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

public enum TitleType implements EnumValue {

  ALTERNATIVE_TITLE("AlternativeTitle"),
  SUBTITLE("Subtitle"),
  TRANSLATED_TITLE("TranslatedTitle"),
  OTHER("Other");

  private final String value;

  TitleType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static TitleType fromValue(String v) {
    for (TitleType c : TitleType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
