package life.catalogue.parser;


import life.catalogue.api.vocab.ThreatStatus;

/**
 * Parses TaxonomicStatus
 */
public class ThreatStatusParser extends EnumParser<ThreatStatus> {
  public static final ThreatStatusParser PARSER = new ThreatStatusParser();

  public ThreatStatusParser() {
    super("threatstatus.csv", ThreatStatus.class);
    for (var status : ThreatStatus.values()) {
      add(status.code(), status);
    }
  }

}
