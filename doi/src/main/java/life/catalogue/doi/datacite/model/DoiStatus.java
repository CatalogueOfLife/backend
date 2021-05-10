package life.catalogue.doi.datacite.model;

/**
 * Following the 3 datacite states: https://support.datacite.org/docs/doi-states
 */
public enum DoiStatus {
  DRAFT,
  REGISTERED,
  FINDABLE;

  public static DoiStatus fromString(String status) {
    for (DoiStatus s : values()) {
      if (status.equalsIgnoreCase(s.name())) {
        return s;
      }
    }
    return null;
  }

}
