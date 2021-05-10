package life.catalogue.doi.datacite.model;

public enum NameType {

  ORGANIZATIONAL("Organizational"),
  PERSONAL("Personal");

  private final String value;

  NameType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static NameType fromValue(String v) {
    for (NameType c : NameType.values()) {
      if (c.value.equals(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
