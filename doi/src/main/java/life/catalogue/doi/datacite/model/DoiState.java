package life.catalogue.doi.datacite.model;

/**
 * Following the 3 datacite states: https://support.datacite.org/docs/doi-states
 */
public enum DoiState {
  DRAFT,
  REGISTERED,
  FINDABLE;

  public static DoiState fromString(String status) {
    for (DoiState s : values()) {
      if (status.equalsIgnoreCase(s.name())) {
        return s;
      }
    }
    return null;
  }

}
