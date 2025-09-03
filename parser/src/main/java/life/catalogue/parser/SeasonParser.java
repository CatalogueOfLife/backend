package life.catalogue.parser;


import life.catalogue.api.vocab.Season;

/**
 * Parses TaxonomicStatus
 */
public class SeasonParser extends EnumParser<Season> {
  public static final SeasonParser PARSER = new SeasonParser();

  public SeasonParser() {
    super("season.csv", Season.class);
  }

}
