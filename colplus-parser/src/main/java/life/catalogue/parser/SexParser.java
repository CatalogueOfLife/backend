package life.catalogue.parser;

import life.catalogue.api.vocab.Sex;

/**
 *
 */
public class SexParser extends EnumParser<Sex> {
  public static final SexParser PARSER = new SexParser();

  public SexParser() {
    super("sex.csv", Sex.class);
    for (Sex s : Sex.values()) {
      add(String.valueOf(s.symbol), s);
    }
  }

}
