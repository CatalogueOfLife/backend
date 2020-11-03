package life.catalogue.parser;

import life.catalogue.api.vocab.SpeciesInteractionType;

/**
 *
 */
public class SpeciesInteractionTypeParser extends EnumParser<SpeciesInteractionType> {
  public static final SpeciesInteractionTypeParser PARSER = new SpeciesInteractionTypeParser();

  public SpeciesInteractionTypeParser() {
    super("speciesinteractiontype.csv", SpeciesInteractionType.class);
  }
  
}
