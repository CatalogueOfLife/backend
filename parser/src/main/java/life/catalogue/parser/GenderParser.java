package life.catalogue.parser;

import life.catalogue.api.vocab.Gender;

/**
 *
 */
public class GenderParser extends EnumParser<Gender> {
  public static final GenderParser PARSER = new GenderParser();

  public GenderParser() {
    super("gender.csv", Gender.class);
  }

}
