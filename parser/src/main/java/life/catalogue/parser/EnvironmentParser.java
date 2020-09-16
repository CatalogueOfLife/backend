package life.catalogue.parser;

import life.catalogue.api.vocab.Environment;

/**
 *
 */
public class EnvironmentParser extends EnumParser<Environment> {
  public static final EnvironmentParser PARSER = new EnvironmentParser();
  
  public EnvironmentParser() {
    super("environment.csv", Environment.class);
  }
  
}
