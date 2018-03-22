package org.col.admin.task.importer.acef;

import org.col.parser.FuzzyDateParser;

public class AcefDateParser extends FuzzyDateParser {

  public static final AcefDateParser PARSER = new AcefDateParser();

  private AcefDateParser() {
    super(FuzzyDateParser.class.getResourceAsStream("acef.fuzzy-date.properties"));
  }

}
