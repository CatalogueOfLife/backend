package org.col.parser;

public class AcefDateParser extends DateParser {

  public static final AcefDateParser PARSER = new AcefDateParser();

  private AcefDateParser() {
    super(DateParser.class.getResourceAsStream("acef.fuzzy-date.properties"));
  }

}
