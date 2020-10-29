package life.catalogue.parser;

import life.catalogue.api.vocab.EstimateType;

/**
 *
 */
public class EstimateTypeParser extends EnumParser<EstimateType> {
  public static final EstimateTypeParser PARSER = new EstimateTypeParser();

  public EstimateTypeParser() {
    super("estimatetype.csv", EstimateType.class);
  }
  
}
