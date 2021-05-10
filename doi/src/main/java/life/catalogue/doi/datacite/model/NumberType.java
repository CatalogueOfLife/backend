package life.catalogue.doi.datacite.model;

public enum NumberType {

  ARTICLE("Article"),
  CHAPTER("Chapter"),
  REPORT("Report"),
  OTHER("Other");

  private final String value;

  NumberType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static NumberType fromValue(String v) {
    for (NumberType c : NumberType.values()) {
      if (c.value.equals(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
