package org.col.parser;

/**
 * Parses integers throwing UnparsableException in case the value is not empty but unparsable.
 */
public class BooleanParser extends MapBasedParser<Boolean> {
  public static final Parser<Boolean> PARSER = new BooleanParser();

  public BooleanParser() {
    super(Boolean.class);
    addMappings("boolean.csv");
  }
  
  @Override
  protected Boolean mapNormalisedValue(String upperCaseValue) {
    return Boolean.parseBoolean(upperCaseValue);
  }
  
  @Override
  String normalize(String x) {
    // -1 being an exception from the normalize rule
    if (x != null && x.trim().equals("-1")) {
      return "-1";
    }
    return super.normalize(x);
  }
}
