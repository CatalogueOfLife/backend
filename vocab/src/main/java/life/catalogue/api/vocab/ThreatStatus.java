package life.catalogue.api.vocab;

/**
 * IUCN threat status vocabulary.
 * https://www.iucnredlist.org/resources/categories-and-criteria
 */
public enum ThreatStatus {
  EXTINCT("EX"),
  EXTINCT_IN_THE_WILD("EW"),
  CRITICALLY_ENDANGERED("CR"),
  ENDANGERED("EN"),
  VULNERABLE("VU"),
  NEAR_THREATENED("NT"),
  LEAST_CONCERN("LC"),
  DATA_DEFICIENT("DD"),
  NOT_EVALUATED("NE");

  private final String code;

  ThreatStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
