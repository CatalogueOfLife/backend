package life.catalogue.parser;


import life.catalogue.api.vocab.DistributionStatus;

/**
 * Parses DistributionStatus
 */
public class DistributionStatusParser extends EnumParser<DistributionStatus> {
  public static final DistributionStatusParser PARSER = new DistributionStatusParser();

  public DistributionStatusParser() {
    super("distributionstatus.csv", DistributionStatus.class);
  }

}
