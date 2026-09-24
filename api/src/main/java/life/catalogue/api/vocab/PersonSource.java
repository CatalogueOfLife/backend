package life.catalogue.api.vocab;



/**
 * Who a line of the registry came from. A harvest never removes or overwrites a curated line.
 */
public enum PersonSource {
  CURATED, IPNI, ZOOBANK, WIKIDATA;

  /** the lower case form the files hold */
  public String value() {
    return name().toLowerCase();
  }

  public static PersonSource of(String value) {
    return valueOf(value.toUpperCase());
  }
}
