package life.catalogue.matching.person;

/**
 * Who a line of the registry came from. A harvest never removes or overwrites a curated line.
 */
public enum Provenance {
  CURATED, IPNI, ZOOBANK, WIKIDATA;

  /** the lower case form the files hold */
  public String value() {
    return name().toLowerCase();
  }

  public static Provenance of(String value) {
    return valueOf(value.toUpperCase());
  }
}
