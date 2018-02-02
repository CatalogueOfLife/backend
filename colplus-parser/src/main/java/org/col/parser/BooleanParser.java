package org.col.parser;

/**
 * Parses integers throwing UnparsableException in case the value is not empty but unparsable.
 */
public class BooleanParser extends GbifParserBased<Boolean, Boolean> {
  public static final Parser<Boolean> PARSER = new BooleanParser();

  public BooleanParser() {
    super(Boolean.class, org.gbif.common.parsers.BooleanParser.getInstance());
  }

  @Override
  Boolean convertFromGbif(Boolean value) {
    return value;
  }

}
